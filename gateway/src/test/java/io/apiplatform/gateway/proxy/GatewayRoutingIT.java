package io.apiplatform.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sun.net.httpserver.HttpServer;
import io.apiplatform.gateway.config.Snapshot;
import io.apiplatform.gateway.config.SnapshotHolder;
import io.apiplatform.gateway.config.SnapshotPoller;
import io.apiplatform.gateway.resilience.TargetSelector;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Full data-plane tests: real gateway on a random port, real HTTP backend (JDK HttpServer) on localhost.
 * The control plane is not involved: snapshots are applied through the same validated path the poller uses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.control-plane-url=", "gateway.allowed-hosts=localhost", "gateway.allow-loopback=true",
        "gateway.jwt-secret=test-only-secret-test-only-secret-1234", "gateway.jwt-issuer=api-platform-test",
        "gateway.health-check-ms=3600000", "gateway.telemetry-flush-ms=3600000", "gateway.retry-base-delay-ms=5",
        "gateway.breaker-failure-threshold=5", "gateway.breaker-open-ms=60000", "gateway.max-concurrent-per-route=4",
        "management.tracing.sampling.probability=0.0"})
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability // re-enables metric export disabled by default in tests
class GatewayRoutingIT {
    static HttpServer backend;
    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicInteger flakyCalls = new AtomicInteger();
    static final AtomicReference<String> lastHeaders = new AtomicReference<>("");
    static String backendUrl;
    static final String API_KEY = "ktestprefix.0123456789012345678901234567890123456789";
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @LocalServerPort int port;
    @Autowired SnapshotPoller poller;
    @Autowired SnapshotHolder holder;
    @Autowired TargetSelector selector;

    @BeforeAll
    static void startBackend() throws IOException {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        backend.createContext("/", ex -> {
            calls.incrementAndGet();
            String path = ex.getRequestURI().getPath();
            int status = 200;
            String body;
            try {
                if (path.equals("/slow")) Thread.sleep(1500);
                if (path.equals("/flaky") && flakyCalls.incrementAndGet() <= 2) status = 503;
                if (path.equals("/down")) status = 503;
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            StringBuilder h = new StringBuilder();
            ex.getRequestHeaders().forEach((k, v) -> h.append(k.toLowerCase()).append('=').append(String.join(",", v)).append(';'));
            lastHeaders.set(h.toString());
            byte[] in = ex.getRequestBody().readAllBytes();
            body = "{\"path\":\"" + path + "\",\"query\":\"" + ex.getRequestURI().getRawQuery() + "\",\"bytes\":" + in.length + "}";
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.getResponseHeaders().add("X-Backend", "yes");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        backend.start();
        backendUrl = "http://localhost:" + backend.getAddress().getPort();
    }

    @AfterAll
    static void stopBackend() { backend.stop(0); }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    Snapshot.Policy policy(String auth, int ratePerMin, int timeoutMs, boolean retry, Set<String> methods, long maxBytes) {
        return new Snapshot.Policy(auth, null, methods, ratePerMin, ratePerMin, "FIXED_WINDOW", timeoutMs, maxBytes, null, retry);
    }

    Snapshot.Route route(String slug, String base, Snapshot.Policy p, String... targets) {
        return new Snapshot.Route(slug, "v1", base, java.util.Arrays.stream(targets).map(t -> new Snapshot.Target(t, 1, "/health")).toList(), p, 1);
    }

    void apply(Snapshot.Route... routes) {
        var key = new Snapshot.Key("ktestprefix", sha256(API_KEY), Instant.now().plusSeconds(3600), "acme",
                List.of(routes.length == 0 ? "none" : routes[0].apiSlug()));
        boolean ok = poller.applyValidated(new Snapshot("v-" + System.nanoTime(), Instant.now(), List.of(routes), List.of(key)));
        assertThat(ok).isTrue();
    }

    @BeforeEach
    void reset() {
        calls.set(0);
        flakyCalls.set(0);
        // closed breakers/health for every test: breakers are keyed by backend url, so use fresh state explicitly
        selector.breaker(backendUrl).onSuccess();
        selector.setHealthy(backendUrl, true);
    }

    HttpResponse<String> call(String method, String path, String body, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------------ routing
    @Test
    void unknownRouteReturnsStandardized404WithCorrelationId() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        var r = call("GET", "/gw/nothing/here", null);
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).contains("\"code\":\"route_not_found\"").contains("correlationId");
        assertThat(r.headers().firstValue("X-Correlation-Id")).isPresent();
        assertThat(calls.get()).isZero();
    }

    @Test
    void routesTrafficRewritesPathAndPropagatesCorrelationId() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET", "POST"), 1000), backendUrl));
        var r = call("GET", "/gw/orders/v1/items/7?x=1&y=two", null, "X-Correlation-Id", "corr-12345678");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"path\":\"/items/7\"").contains("x=1&y=two"); // base path stripped, query kept
        assertThat(r.headers().firstValue("X-Correlation-Id")).contains("corr-12345678");
        assertThat(r.headers().firstValue("X-Backend")).contains("yes");
        assertThat(lastHeaders.get()).contains("x-correlation-id=corr-12345678").contains("x-forwarded-for=");
    }

    @Test
    void missingOrMalformedCorrelationIdIsReplacedWithGeneratedOne() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        var r = call("GET", "/gw/orders/v1/x", null);
        assertThat(r.headers().firstValue("X-Correlation-Id").orElseThrow()).hasSize(36);
        var bad = call("GET", "/gw/orders/v1/x", null, "X-Correlation-Id", "short");
        assertThat(bad.headers().firstValue("X-Correlation-Id").orElseThrow()).isNotEqualTo("short");
    }

    @Test
    void disallowedMethodGets405WithAllowHeader() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        var r = call("DELETE", "/gw/orders/v1/items/1", null);
        assertThat(r.statusCode()).isEqualTo(405);
        assertThat(r.headers().firstValue("Allow")).contains("GET");
        assertThat(calls.get()).isZero();
    }

    @Test
    void pathTraversalAttemptsNeverReachTheBackend() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        assertThat(call("GET", "/gw/orders/v1/%2e%2e/admin", null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/gw/orders/v1/a%2f..%2fb", null).statusCode()).isIn(400, 404);
        assertThat(calls.get()).isZero();
    }

    // ------------------------------------------------------------------ authentication
    @Test
    void apiKeyPolicyRejectsMissingWrongExpiredAndUnsubscribedKeys() throws Exception {
        apply(route("orders", "/orders/v1", policy("API_KEY", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        assertThat(call("GET", "/gw/orders/v1/x", null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null, "X-API-Key", "ktestprefix.wrong-secret").statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null, "X-API-Key", "unknownprefix.whatever").statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null, "X-API-Key", "no-dot-at-all").statusCode()).isEqualTo(401);
        var r = call("GET", "/gw/orders/v1/x", null, "X-API-Key", "bad");
        assertThat(r.body()).contains("unauthorized").doesNotContain("api key"); // no detail leak
        assertThat(r.headers().firstValue("WWW-Authenticate")).isPresent();
        assertThat(calls.get()).isZero();

        // expired key
        var expired = new Snapshot.Key("ktestprefix", sha256(API_KEY), Instant.now().minusSeconds(5), "acme", List.of("orders"));
        poller.applyValidated(new Snapshot("v-exp" + System.nanoTime(), Instant.now(),
                List.of(route("orders", "/orders/v1", policy("API_KEY", 100000, 2000, false, Set.of("GET"), 1000), backendUrl)), List.of(expired)));
        assertThat(call("GET", "/gw/orders/v1/x", null, "X-API-Key", API_KEY).statusCode()).isEqualTo(401);

        // valid key but not subscribed to this API
        var other = new Snapshot.Key("ktestprefix", sha256(API_KEY), Instant.now().plusSeconds(60), "acme", List.of("billing"));
        poller.applyValidated(new Snapshot("v-sub" + System.nanoTime(), Instant.now(),
                List.of(route("orders", "/orders/v1", policy("API_KEY", 100000, 2000, false, Set.of("GET"), 1000), backendUrl)), List.of(other)));
        assertThat(call("GET", "/gw/orders/v1/x", null, "X-API-Key", API_KEY).statusCode()).isEqualTo(401);
    }

    @Test
    void validKeyIsAcceptedCredentialsAreNotForwardedAndIdentityCannotBeSpoofed() throws Exception {
        apply(route("orders", "/orders/v1", policy("API_KEY", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        var r = call("GET", "/gw/orders/v1/x", null, "X-API-Key", API_KEY, "X-Consumer-Id", "admin-spoof", "Authorization", "Bearer secret");
        assertThat(r.statusCode()).isEqualTo(200);
        String seen = lastHeaders.get();
        assertThat(seen).contains("x-consumer-id=acme").doesNotContain("admin-spoof")
                .doesNotContain("x-api-key").doesNotContain("authorization");
    }

    @Test
    void jwtPolicyVerifiesSignatureIssuerExpiryAndRole() throws Exception {
        var p = new Snapshot.Policy("JWT", "orders:read", Set.of("GET"), 100000, 100000, "FIXED_WINDOW", 2000, 1000L, null, false);
        apply(route("orders", "/orders/v1", p, backendUrl));
        Algorithm alg = Algorithm.HMAC256("test-only-secret-test-only-secret-1234");
        String good = JWT.create().withIssuer("api-platform-test").withSubject("svc-a").withClaim("roles", List.of("orders:read"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(alg);
        String noRole = JWT.create().withIssuer("api-platform-test").withSubject("svc-b").withClaim("roles", List.of("other"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(alg);
        String expired = JWT.create().withIssuer("api-platform-test").withSubject("svc-a").withClaim("roles", List.of("orders:read"))
                .withExpiresAt(new Date(System.currentTimeMillis() - 60_000)).sign(alg);
        String wrongIssuer = JWT.create().withIssuer("evil").withSubject("svc-a").withClaim("roles", List.of("orders:read"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(alg);
        String forged = JWT.create().withIssuer("api-platform-test").withSubject("svc-a").withClaim("roles", List.of("orders:read"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(Algorithm.HMAC256("another-secret-another-secret-123456"));
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + good).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + noRole).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + expired).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + wrongIssuer).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + forged).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer not.a.jwt").statusCode()).isEqualTo(401);
        assertThat(call("GET", "/gw/orders/v1/x", null).statusCode()).isEqualTo(401);
        // regression cases added with the java-jwt upgrade (docs/governance/vulnerability-remediation.md):
        String notYetValid = JWT.create().withIssuer("api-platform-test").withSubject("svc-a").withClaim("roles", List.of("orders:read"))
                .withNotBefore(new Date(System.currentTimeMillis() + 3_600_000)).withExpiresAt(new Date(System.currentTimeMillis() + 7_200_000)).sign(alg);
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + notYetValid).statusCode()).isEqualTo(401);
        // algorithm-confusion attempt: unsigned ("alg":"none") token with a valid-looking payload must never be accepted
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"api-platform-test\",\"sub\":\"evil\",\"roles\":[\"orders:read\"],\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        assertThat(call("GET", "/gw/orders/v1/x", null, "Authorization", "Bearer " + header + "." + payload + ".").statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ rate limiting / size / backpressure
    @Test
    void rateLimitReturns429WithHeadersAfterTheLimit() throws Exception {
        apply(route("ratelimited", "/ratelimited/v1", policy("API_KEY", 3, 2000, false, Set.of("GET"), 1000), backendUrl));
        for (int i = 0; i < 3; i++) {
            var ok = call("GET", "/gw/ratelimited/v1/x", null, "X-API-Key", API_KEY);
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.headers().firstValue("X-RateLimit-Limit")).contains("3");
        }
        var limited = call("GET", "/gw/ratelimited/v1/x", null, "X-API-Key", API_KEY);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(limited.headers().firstValue("X-RateLimit-Remaining")).contains("0");
        assertThat(limited.body()).contains("rate_limited");
        assertThat(calls.get()).isEqualTo(3); // the 4th never reached the backend
    }

    @Test
    void oversizedRequestBodyIsRejectedBeforeReachingTheBackend() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("POST"), 100), backendUrl));
        assertThat(call("POST", "/gw/orders/v1/x", "a".repeat(101)).statusCode()).isEqualTo(413);
        assertThat(call("POST", "/gw/orders/v1/x", "a".repeat(100)).statusCode()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ backend failure handling
    @Test
    void backendTimeoutReturns504() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 300, false, Set.of("GET"), 1000), backendUrl));
        long t0 = System.nanoTime();
        var r = call("GET", "/gw/orders/v1/slow", null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(r.statusCode()).isEqualTo(504);
        assertThat(r.body()).contains("gateway_timeout");
        assertThat(ms).isLessThan(1400); // returned at ~300ms, did not wait for the 1500ms backend
    }

    @Test
    void unreachableBackendReturns502NotAnInternalError() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 1000, false, Set.of("GET"), 1000), "http://localhost:1"));
        var r = call("GET", "/gw/orders/v1/x", null);
        assertThat(r.statusCode()).isEqualTo(502);
        assertThat(r.body()).doesNotContain("localhost:1").doesNotContain("Exception"); // no internal detail
    }

    @Test
    void safeMethodsAreRetriedOnTransientFailureButPostIsNot() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 3000, true, Set.of("GET", "POST"), 1000), backendUrl));
        var get = call("GET", "/gw/orders/v1/flaky", null);
        assertThat(get.statusCode()).isEqualTo(200); // 503, 503, then 200
        assertThat(calls.get()).isEqualTo(3);

        calls.set(0);
        var post = call("POST", "/gw/orders/v1/down", "{}");
        assertThat(post.statusCode()).isEqualTo(503); // passed through, exactly one attempt: unsafe methods are never retried
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesAreBoundedByAttemptLimit() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 5000, true, Set.of("GET"), 1000), backendUrl));
        var r = call("GET", "/gw/orders/v1/down", null);
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(calls.get()).isEqualTo(3); // maxRetryAttempts=3: a failing dependency is not hammered without bound
    }

    @Test
    void circuitBreakerOpensAndGatewayDegradesWithoutCallingTheFailingBackend() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 1000, 3000, false, Set.of("GET"), 1000), backendUrl));
        for (int i = 0; i < 5; i++) assertThat(call("GET", "/gw/orders/v1/down", null).statusCode()).isEqualTo(503);
        assertThat(calls.get()).isEqualTo(5);
        var degraded = call("GET", "/gw/orders/v1/down", null);
        assertThat(degraded.statusCode()).isEqualTo(503);
        assertThat(degraded.body()).contains("no_healthy_backend");
        assertThat(degraded.headers().firstValue("Retry-After")).isPresent();
        assertThat(calls.get()).isEqualTo(5); // fast-fail: the 6th request never hit the backend
    }

    @Test
    void secondTargetTakesOverWhenFirstIsUnhealthy() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 1000, 3000, false, Set.of("GET"), 1000), "http://localhost:1", backendUrl));
        selector.setHealthy("http://localhost:1", false);
        for (int i = 0; i < 4; i++) assertThat(call("GET", "/gw/orders/v1/x", null).statusCode()).isEqualTo(200);
        selector.setHealthy("http://localhost:1", true);
    }

    @Test
    void bulkheadShedsLoadWhenARouteIsSaturated() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 10_000, 5000, false, Set.of("GET"), 1000), backendUrl));
        int n = 12; // max-concurrent-per-route = 4, backend /slow takes 1.5s
        var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < n; i++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/gw/orders/v1/slow")).GET().build();
            futures.add(http.sendAsync(req, HttpResponse.BodyHandlers.ofString()));
        }
        long shed = futures.stream().map(java.util.concurrent.CompletableFuture::join).filter(r -> r.statusCode() == 503).count();
        long ok = futures.stream().map(java.util.concurrent.CompletableFuture::join).filter(r -> r.statusCode() == 200).count();
        assertThat(shed).isGreaterThanOrEqualTo(1);
        assertThat(ok).isGreaterThanOrEqualTo(1);
        assertThat(shed + ok).isEqualTo(n);
    }

    // ------------------------------------------------------------------ safe configuration updates / SSRF
    @Test
    void routeWithDisallowedBackendHostIsRejectedAndNeverServed() throws Exception {
        boolean accepted = poller.applyValidated(new Snapshot("v-ssrf", Instant.now(), List.of(
                route("evil", "/evil/v1", policy("NONE", 100000, 1000, false, Set.of("GET"), 1000), "http://169.254.169.254")), List.of()));
        assertThat(accepted).isFalse(); // no valid routes -> snapshot rejected, previous config retained
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        assertThat(call("GET", "/gw/evil/v1/latest/meta-data", null).statusCode()).isEqualTo(404);
    }

    @Test
    void invalidSnapshotDoesNotReplaceTheLastKnownGoodConfiguration() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        String good = holder.current().snapshot().version();
        var broken = new Snapshot.Route("broken", "v1", "/broken/v1", List.of(new Snapshot.Target(backendUrl, 1, "/h")), null, 1); // no policy
        assertThat(poller.applyValidated(new Snapshot("v-bad", Instant.now(), List.of(broken), List.of()))).isFalse();
        assertThat(holder.current().snapshot().version()).isEqualTo(good);
        assertThat(call("GET", "/gw/orders/v1/x", null).statusCode()).isEqualTo(200);
    }

    @Test
    void partiallyInvalidSnapshotAppliesOnlyTheValidRoutes() throws Exception {
        var broken = new Snapshot.Route("broken", "v1", "/broken/v1", List.of(new Snapshot.Target("http://evil.example.org", 1, "/h")),
                policy("NONE", 100000, 1000, false, Set.of("GET"), 100), 1);
        var good = route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl);
        assertThat(poller.applyValidated(new Snapshot("v-partial", Instant.now(), List.of(broken, good), List.of()))).isTrue();
        assertThat(call("GET", "/gw/orders/v1/x", null).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/gw/broken/v1/x", null).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ observability / readiness
    @Test
    void prometheusExposesRouteLevelMetricsWithBoundedLabels() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        call("GET", "/gw/orders/v1/x", null);
        call("GET", "/gw/random-" + System.nanoTime(), null); // unknown paths must not create new label values
        String metrics = call("GET", "/actuator/prometheus", null).body();
        assertThat(metrics).contains("gateway_requests_seconds_count{application=\"gateway\",route=\"orders/v1\",status_class=\"2xx\"")
                .contains("route=\"unmatched\"").doesNotContain("random-");
    }

    @Test
    void readinessIsUpOnceConfigurationIsLoaded() throws Exception {
        apply(route("orders", "/orders/v1", policy("NONE", 100000, 2000, false, Set.of("GET"), 1000), backendUrl));
        var r = call("GET", "/actuator/health/readiness", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("UP");
    }
}
