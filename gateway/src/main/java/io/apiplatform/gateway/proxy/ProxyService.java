package io.apiplatform.gateway.proxy;

import io.apiplatform.gateway.GatewayProperties;
import io.apiplatform.gateway.config.DestinationGuard;
import io.apiplatform.gateway.config.Snapshot;
import io.apiplatform.gateway.resilience.TargetSelector;
import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Forwards one request to a backend with end-to-end timeout, safe retries and circuit breaking.
 * Retry rules (to avoid duplicating side effects): only GET/HEAD/OPTIONS, only when the policy enables retries, only on
 * connection failures/502/503/504, never on timeouts of unsafe methods, with exponential backoff + full jitter, and
 * never beyond the policy's end-to-end timeout budget (this bounds retry storms).
 */
@Component
public class ProxyService {
    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> HOP_BY_HOP = Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
    private static final Set<String> STRIPPED_REQUEST = Set.of("authorization", "x-api-key", "x-consumer-id", "x-forwarded-for",
            "x-forwarded-proto", "x-forwarded-host", "x-correlation-id", "traceparent", "tracestate", "baggage");

    public record Result(int status, Map<String, List<String>> headers, byte[] body, String errorCode, boolean timedOut, String target) {
        public boolean failed() { return errorCode != null; }
    }

    public record Request(String method, String remainder, String query, Map<String, List<String>> headers, byte[] body,
                          String correlationId, String consumerId, String remoteIp, String scheme, String host) {}

    private final GatewayProperties props;
    private final TargetSelector selector;
    private final DestinationGuard guard;
    private final TelemetryRecorder telemetry;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();

    public ProxyService(GatewayProperties props, TargetSelector selector, DestinationGuard guard, TelemetryRecorder telemetry,
                        ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.props = props; this.selector = selector; this.guard = guard; this.telemetry = telemetry;
        this.tracer = tracer; this.propagator = propagator;
    }

    public Result forward(Snapshot.Route route, Request req) {
        Snapshot.Policy policy = route.policy();
        long start = System.nanoTime();
        long budgetNanos = Duration.ofMillis(policy.timeoutMs()).toNanos();
        boolean retryable = Boolean.TRUE.equals(policy.retryEnabled()) && SAFE_METHODS.contains(req.method());
        int attempts = retryable ? props.maxRetryAttempts() : 1;
        Set<String> tried = new HashSet<>();
        Result last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long remaining = budgetNanos - (System.nanoTime() - start);
            if (remaining <= 0) return last != null ? last : new Result(504, Map.of(), new byte[0], "gateway_timeout", true, null);
            Optional<Snapshot.Target> pick = selector.select(route, tried);
            if (pick.isEmpty()) {
                return last != null ? last : new Result(503, Map.of(), new byte[0], "no_healthy_backend", false, null);
            }
            Snapshot.Target target = pick.get();
            if (!guard.allowed(target.url())) { // SSRF / DNS-rebinding re-check at connect time
                log.error("blocked proxy to disallowed destination {}", target.url());
                telemetry.event("INVALID_CONFIG", "ERROR", route.id(), "blocked disallowed destination " + target.url(), req.correlationId());
                tried.add(target.url());
                last = new Result(502, Map.of(), new byte[0], "destination_not_allowed", false, null);
                continue;
            }
            Result r = attemptOnce(route, target, req, Duration.ofNanos(remaining));
            boolean backendFailure = r.failed() || r.status() == 502 || r.status() == 503 || r.status() == 504;
            if (!backendFailure) {
                selector.success(target.url());
                return r;
            }
            selector.failure(route.id(), target.url(), req.correlationId());
            last = r;
            tried.add(target.url());
            // timeouts of unsafe methods are never retried; safe methods may retry on another target
            if (attempt < attempts && !(r.timedOut() && !retryable)) {
                telemetry.retry(route.id());
                backoff(attempt, budgetNanos - (System.nanoTime() - start));
                if (tried.size() >= route.targets().size()) tried.clear(); // single-target routes may retry the same target
            } else {
                break;
            }
        }
        return last;
    }

    private Result attemptOnce(Snapshot.Route route, Snapshot.Target target, Request req, Duration timeout) {
        try {
            String base = target.url().replaceAll("/+$", "");
            String path = req.remainder() == null || req.remainder().isEmpty() ? "/" : req.remainder();
            URI uri = URI.create(base + path + (req.query() == null ? "" : "?" + req.query()));
            HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(timeout);
            req.headers().forEach((name, values) -> {
                String n = name.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP.contains(n) || STRIPPED_REQUEST.contains(n)) return;
                values.forEach(v -> b.header(name, v));
            });
            // identity is passed as gateway-asserted headers; inbound credentials are NOT forwarded to backends
            b.header("X-Correlation-Id", req.correlationId());
            b.header("X-Consumer-Id", req.consumerId() == null ? "anonymous" : req.consumerId());
            b.header("X-Forwarded-For", req.remoteIp());
            b.header("X-Forwarded-Proto", req.scheme());
            b.header("X-Forwarded-Host", req.host() == null ? "" : req.host());
            injectTrace(b);
            HttpRequest.BodyPublisher body = req.body() == null || req.body().length == 0
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(req.body());
            b.method(req.method(), body);

            HttpResponse<InputStream> res = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] payload;
            try (InputStream in = res.body()) {
                payload = readBounded(in, props.maxResponseBytes());
            }
            if (payload == null) return new Result(502, Map.of(), new byte[0], "response_too_large", false, target.url());
            Map<String, List<String>> headers = new LinkedHashMap<>();
            res.headers().map().forEach((k, v) -> {
                if (!HOP_BY_HOP.contains(k.toLowerCase(Locale.ROOT)) && !k.startsWith(":")) headers.put(k, v);
            });
            return new Result(res.statusCode(), headers, payload, null, false, target.url());
        } catch (HttpTimeoutException e) {
            return new Result(504, Map.of(), new byte[0], "gateway_timeout", true, target.url());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(502, Map.of(), new byte[0], "interrupted", false, target.url());
        } catch (IOException | RuntimeException e) {
            log.warn("backend call failed route={} target={} error={}", route.id(), target.url(), e.getClass().getSimpleName());
            return new Result(502, Map.of(), new byte[0], "bad_gateway", false, target.url());
        }
    }

    private void injectTrace(HttpRequest.Builder b) {
        Tracer t = tracer.getIfAvailable();
        Propagator p = propagator.getIfAvailable();
        if (t == null || p == null) return;
        Span span = t.currentSpan();
        if (span != null) p.inject(span.context(), b, (builder, k, v) -> builder.header(k, v));
    }

    private void backoff(int attempt, long remainingNanos) {
        long ceiling = Math.min(props.retryBaseDelayMs() * (1L << Math.min(attempt - 1, 6)), 2_000);
        long delay = ThreadLocalRandom.current().nextLong(ceiling + 1); // full jitter
        long cap = Math.max(0, remainingNanos / 2_000_000);             // never sleep more than half the remaining budget
        try {
            Thread.sleep(Math.min(delay, cap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static byte[] readBounded(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > max) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
