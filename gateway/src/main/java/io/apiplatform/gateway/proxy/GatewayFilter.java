package io.apiplatform.gateway.proxy;

import io.apiplatform.gateway.GatewayProperties;
import io.apiplatform.gateway.auth.Authenticator;
import io.apiplatform.gateway.auth.IpAllowList;
import io.apiplatform.gateway.config.RouteTable;
import io.apiplatform.gateway.config.Snapshot;
import io.apiplatform.gateway.config.SnapshotHolder;
import io.apiplatform.gateway.ratelimit.RateLimiter;
import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The request pipeline of the gateway (everything under /gw/). Order matters and is deliberate:
 * correlation id -> route resolution -> method -> IP allowlist -> path safety -> authentication -> authorization ->
 * rate limit -> request size -> bulkhead (backpressure) -> proxy (timeout/retry/breaker) -> metrics.
 * Cheap, anonymous-safe checks run before expensive or stateful ones; unauthenticated callers learn nothing beyond
 * "route not found" / "method not allowed" / "unauthorized".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(GatewayFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    public static final String CID = "X-Correlation-Id";

    private final SnapshotHolder holder;
    private final Authenticator authenticator;
    private final RateLimiter rateLimiter;
    private final ProxyService proxy;
    private final TelemetryRecorder telemetry;
    private final GatewayProperties props;
    private final Map<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

    public GatewayFilter(SnapshotHolder holder, Authenticator authenticator, RateLimiter rateLimiter, ProxyService proxy,
                         TelemetryRecorder telemetry, GatewayProperties props) {
        this.holder = holder; this.authenticator = authenticator; this.rateLimiter = rateLimiter;
        this.proxy = proxy; this.telemetry = telemetry; this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/gw/");
    }

    private static final class Outcome {
        String route = TelemetryRecorder.UNMATCHED;
        boolean rateLimited, authFailure, timeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException {
        long start = System.nanoTime();
        String cid = req.getHeader(CID);
        if (cid == null || !SAFE_ID.matcher(cid).matches()) cid = UUID.randomUUID().toString();
        MDC.put("correlationId", cid);
        res.setHeader(CID, cid);
        Outcome o = new Outcome();
        int status = 500;
        try {
            status = handle(req, res, cid, o);
        } catch (RuntimeException e) {
            log.error("gateway pipeline error", e);
            status = error(res, 500, "internal_error", "Unexpected gateway error", cid);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            telemetry.request(o.route, status, ms, o.rateLimited, o.authFailure, o.timeout);
            MDC.remove("correlationId");
        }
    }

    private int handle(HttpServletRequest req, HttpServletResponse res, String cid, Outcome o) throws IOException {
        RouteTable table = holder.current().routes();
        String path = req.getRequestURI().substring(3); // strip "/gw"
        Optional<RouteTable.Match> match = table.match(path);
        if (match.isEmpty()) return error(res, 404, "route_not_found", "No API is registered for this path", cid);
        Snapshot.Route route = match.get().route();
        Snapshot.Policy policy = route.policy();
        o.route = route.id();
        String method = req.getMethod();

        if (!policy.allowedMethods().contains(method)) {
            res.setHeader("Allow", String.join(", ", new TreeSet<>(policy.allowedMethods())));
            return error(res, 405, "method_not_allowed", "Method not allowed for this API", cid);
        }
        String remoteIp = clientIp(req);
        if (!IpAllowList.allowed(policy.ipAllowCidrs(), remoteIp)) {
            return error(res, 403, "ip_not_allowed", "Access from this address is not permitted", cid);
        }
        String remainder = match.get().remainder();
        if (unsafePath(remainder)) return error(res, 400, "invalid_path", "Invalid request path", cid);

        // authentication + authorization
        Authenticator.Result auth = authenticator.authenticate(policy.auth(), route.apiSlug(),
                req.getHeader("X-API-Key"), req.getHeader("Authorization"));
        if (!auth.authenticated()) {
            o.authFailure = true;
            res.setHeader("WWW-Authenticate", "API_KEY".equals(policy.auth()) ? "ApiKey" : "Bearer");
            log.info("auth failure route={} reason={}", route.id(), auth.failure());
            return error(res, 401, "unauthorized", "Authentication required", cid);
        }
        if (policy.requiredRole() != null && !auth.roles().contains(policy.requiredRole())) {
            return error(res, 403, "forbidden", "Insufficient permissions", cid);
        }

        // rate limit: per consumer and per route (anonymous callers are limited per source IP)
        String subject = "anonymous".equals(auth.consumerId()) ? "ip:" + remoteIp : "c:" + auth.consumerId();
        RateLimiter.Decision d = rateLimiter.check(route.id() + ":" + subject, policy.rateLimitPerMinute(),
                policy.burst() == null ? policy.rateLimitPerMinute() : policy.burst(),
                RateLimiter.Algorithm.parse(policy.rateAlgorithm()));
        res.setHeader("X-RateLimit-Limit", String.valueOf(d.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, d.remaining())));
        res.setHeader("X-RateLimit-Reset", String.valueOf(d.resetSeconds()));
        if (d.unavailable()) {
            res.setHeader("Retry-After", "5");
            return error(res, 503, "rate_limiter_unavailable", "Rate limiter unavailable; try again shortly", cid);
        }
        if (!d.allowed()) {
            o.rateLimited = true;
            res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
            return error(res, 429, "rate_limited", "Rate limit exceeded", cid);
        }

        // request size: declared length first, then bounded streaming read (covers chunked bodies)
        long max = policy.maxRequestBytes() == null ? 1_048_576 : policy.maxRequestBytes();
        if (req.getContentLengthLong() > max) return error(res, 413, "payload_too_large", "Request body too large", cid);
        byte[] body = readBounded(req.getInputStream(), max);
        if (body == null) return error(res, 413, "payload_too_large", "Request body too large", cid);

        // bulkhead: shed load instead of queueing unboundedly when a route is saturated
        Semaphore sem = bulkheads.computeIfAbsent(route.id(), k -> new Semaphore(props.maxConcurrentPerRoute()));
        if (!sem.tryAcquire()) {
            res.setHeader("Retry-After", "1");
            return error(res, 503, "route_saturated", "Too many concurrent requests for this API", cid);
        }
        ProxyService.Result r;
        try {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            Collections.list(req.getHeaderNames()).forEach(n -> headers.put(n, Collections.list(req.getHeaders(n))));
            r = proxy.forward(route, new ProxyService.Request(method, remainder, req.getQueryString(), headers, body, cid,
                    auth.consumerId(), remoteIp, req.getScheme(), req.getHeader("Host")));
        } finally {
            sem.release();
        }
        if (r.timedOut()) o.timeout = true;
        if (r.failed()) {
            return switch (r.errorCode()) {
                case "gateway_timeout" -> error(res, 504, "gateway_timeout", "Backend did not respond in time", cid);
                case "no_healthy_backend" -> {
                    res.setHeader("Retry-After", "5");
                    yield error(res, 503, "no_healthy_backend", "No healthy backend available", cid);
                }
                default -> error(res, 502, r.errorCode(), "Bad gateway", cid);
            };
        }
        res.setStatus(r.status());
        r.headers().forEach((k, vs) -> vs.forEach(v -> res.addHeader(k, v)));
        res.setHeader(CID, cid);
        if (r.body().length > 0 && !"HEAD".equals(method)) res.getOutputStream().write(r.body());
        return r.status();
    }

    /** Standardised error body; never includes internal details (target URLs, stack traces, verifier errors). */
    private int error(HttpServletResponse res, int status, String code, String message, String cid) throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json");
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"correlationId\":\"" + cid + "\"}");
        return status;
    }

    private String clientIp(HttpServletRequest req) {
        if (props.trustForwardedFor()) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /** Rejects dot-segments (also percent-encoded) so a caller cannot climb out of the registered base path. */
    static boolean unsafePath(String remainder) {
        if (remainder == null || remainder.isEmpty()) return false;
        String decoded;
        try {
            decoded = URLDecoder.decode(remainder.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (decoded.indexOf('\\') >= 0 || decoded.indexOf('\0') >= 0 || decoded.contains("//")) return true;
        for (String seg : decoded.split("/")) if (seg.equals("..") || seg.equals(".")) return true;
        return !remainder.startsWith("/");
    }

    private static byte[] readBounded(InputStream in, long max) throws IOException {
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
