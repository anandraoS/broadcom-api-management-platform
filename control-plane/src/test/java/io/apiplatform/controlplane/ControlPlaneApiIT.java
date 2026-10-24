package io.apiplatform.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end control-plane behaviour through the real security filter chain, JPA and Flyway (H2 in MySQL mode). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControlPlaneApiIT {
    static final String PASSWORD = "test-only-password";
    static final String GATEWAY_TOKEN = "test-gateway-token";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // ---------- helpers ----------
    String token(String user) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", user, "password", PASSWORD)))).andExpect(status().isOk()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    String json(Object o) throws Exception { return mapper.writeValueAsString(o); }

    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String user) throws Exception {
        return b.header("Authorization", "Bearer " + token(user)).contentType(MediaType.APPLICATION_JSON);
    }

    JsonNode body(MvcResult r) throws Exception { return mapper.readTree(r.getResponse().getContentAsString()); }

    long createApi(String user, String team) throws Exception {
        String slug = "api-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult r = mvc.perform(as(post("/api/v1/apis"), user).content(json(Map.of(
                "slug", slug, "name", "Test " + slug, "ownerTeam", team, "description", "d"))))
                .andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asLong();
    }

    Map<String, Object> goodPolicy() {
        return Map.of("auth", "API_KEY", "allowedMethods", List.of("GET", "POST"), "rateLimitPerMinute", 100, "burst", 20,
                "rateAlgorithm", "TOKEN_BUCKET", "timeoutMs", 3000, "maxRequestBytes", 4096, "retryEnabled", true);
    }

    // ---------- authentication ----------
    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/apis")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401WithoutRevealingWhichFieldFailed() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "nope"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_credentials"));
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "ghost", "password", PASSWORD))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        var key = new SecretKeySpec("test-only-secret-test-only-secret-1234".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var enc = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant past = Instant.now().minusSeconds(3600);
        var claims = JwtClaimsSet.builder().issuer("api-platform-test").subject("admin").issuedAt(past.minusSeconds(60))
                .expiresAt(past).claim("roles", List.of("ADMIN")).build();
        String expired = enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/apis").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromWrongIssuerOrSignatureIsRejected() throws Exception {
        var wrongKey = new SecretKeySpec("another-secret-another-secret-123456".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var enc = new NimbusJwtEncoder(new ImmutableSecret<>(wrongKey));
        var claims = JwtClaimsSet.builder().issuer("api-platform-test").subject("admin").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).claim("roles", List.of("ADMIN")).build();
        String forged = enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/apis").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/apis").header("Authorization", "Bearer garbage.token.value")).andExpect(status().isUnauthorized());
    }

    // ---------- authorization (RBAC + object level) ----------
    @Test
    void viewerCanReadButNotWrite() throws Exception {
        mvc.perform(as(get("/api/v1/apis"), "viewer")).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/apis"), "viewer").content(json(Map.of(
                "slug", "viewer-api", "name", "x", "ownerTeam", "orders")))).andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotModifyAnotherTeamsApi_BOLA() throws Exception {
        long id = createApi("owner-orders", "orders");
        mvc.perform(as(post("/api/v1/apis/" + id + "/lifecycle"), "owner-payments")
                .content(json(Map.of("target", "ACTIVE")))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("not_owner"));
        mvc.perform(as(post("/api/v1/apis/" + id + "/policies"), "owner-payments")
                .content(json(Map.of("config", goodPolicy())))).andExpect(status().isForbidden());
        // owner of the team succeeds
        mvc.perform(as(post("/api/v1/apis/" + id + "/lifecycle"), "owner-orders")
                .content(json(Map.of("target", "ACTIVE")))).andExpect(status().isOk());
    }

    @Test
    void ownerCannotCreateApiForAnotherTeam() throws Exception {
        mvc.perform(as(post("/api/v1/apis"), "owner-orders").content(json(Map.of(
                "slug", "sneaky-api-1", "name", "x", "ownerTeam", "payments")))).andExpect(status().isForbidden());
    }

    // ---------- API registry ----------
    @Test
    void validationErrorsUseProblemFormat() throws Exception {
        mvc.perform(as(post("/api/v1/apis"), "admin").content(json(Map.of("slug", "BAD SLUG", "name", "", "ownerTeam", "orders"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors").isArray()).andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void lifecycleRulesAreEnforced() throws Exception {
        long id = createApi("admin", "platform");
        // DRAFT -> DEPRECATED is not allowed
        mvc.perform(as(post("/api/v1/apis/" + id + "/lifecycle"), "admin").content(json(Map.of("target", "DEPRECATED"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_transition"));
    }

    @Test
    void optimisticLockingRejectsStaleUpdates() throws Exception {
        long id = createApi("admin", "platform");
        MvcResult r = mvc.perform(as(get("/api/v1/apis/" + id), "admin")).andReturn();
        long version = body(r).get("version").asLong();
        mvc.perform(as(put("/api/v1/apis/" + id), "admin").content(json(Map.of("name", "n1", "version", version))))
                .andExpect(status().isOk());
        mvc.perform(as(put("/api/v1/apis/" + id), "admin").content(json(Map.of("name", "n2", "version", version))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("concurrent_modification"));
    }

    @Test
    void searchPaginatesAndWhitelistsSortField() throws Exception {
        for (int i = 0; i < 3; i++) createApi("admin", "platform");
        mvc.perform(as(get("/api/v1/apis?size=2&page=0&sort=name;drop table api_definition&owner=platform"), "viewer"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void searchTermIsParameterisedAgainstSqlInjection() throws Exception {
        mvc.perform(as(get("/api/v1/apis?q=' OR '1'='1"), "viewer")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void idempotencyKeyReplaysSameResponseAndRejectsDifferentBody() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        String slug = "idem-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> req = Map.of("slug", slug, "name", "Idem", "ownerTeam", "platform");
        MvcResult first = mvc.perform(as(post("/api/v1/apis"), "admin").header("Idempotency-Key", key).content(json(req)))
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(as(post("/api/v1/apis"), "admin").header("Idempotency-Key", key).content(json(req)))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replay", "true")).andReturn();
        assertThat(body(second).get("id").asLong()).isEqualTo(body(first).get("id").asLong());
        mvc.perform(as(post("/api/v1/apis"), "admin").header("Idempotency-Key", key)
                .content(json(Map.of("slug", slug + "x", "name", "Other", "ownerTeam", "platform"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("idempotency_key_reuse"));
    }

    // ---------- destinations (SSRF) ----------
    @Test
    void unsafeBackendDestinationsAreRejectedAndAudited() throws Exception {
        long id = createApi("admin", "platform");
        long versionId = addVersion(id, "v1", "/ssrf-" + id);
        for (String bad : List.of("http://169.254.169.254", "http://evil.example.org", "file:///etc/passwd", "http://user:pw@localhost:9001")) {
            mvc.perform(as(post("/api/v1/apis/" + id + "/versions/" + versionId + "/targets"), "admin")
                    .content(json(Map.of("url", bad, "weight", 1)))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("unsafe_destination"));
        }
        mvc.perform(as(get("/api/v1/audit?entityType=API&entityId=" + id), "admin")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action=='TARGET_REJECTED')]").isNotEmpty());
        // credentials from the rejected URL must not be written to the audit trail
        MvcResult audit = mvc.perform(as(get("/api/v1/audit?entityType=API&entityId=" + id), "admin")).andReturn();
        assertThat(audit.getResponse().getContentAsString()).doesNotContain("pw@");
    }

    long addVersion(long apiId, String label, String path) throws Exception {
        MvcResult r = mvc.perform(as(post("/api/v1/apis/" + apiId + "/versions"), "admin")
                .content(json(Map.of("label", label, "basePath", path)))).andExpect(status().isCreated()).andReturn();
        return body(r).get("versions").get(0).get("id").asLong();
    }

    // ---------- policy workflow ----------
    @Test
    void invalidPolicyIsRejectedWithReasons() throws Exception {
        long id = createApi("admin", "platform");
        Map<String, Object> bad = Map.of("auth", "NONE", "allowedMethods", List.of("POST"), "rateLimitPerMinute", 0, "burst", 1,
                "rateAlgorithm", "FIXED_WINDOW", "timeoutMs", 3000, "maxRequestBytes", 10, "retryEnabled", false);
        mvc.perform(as(post("/api/v1/apis/" + id + "/policies"), "admin").content(json(Map.of("config", bad))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors.length()").value(3));
        mvc.perform(as(post("/api/v1/policies/validate"), "viewer").content(json(bad))).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void fourEyesApprovalSnapshotPropagationAndRevert() throws Exception {
        long id = createApi("admin", "platform");
        String slug = body(mvc.perform(as(get("/api/v1/apis/" + id), "admin")).andReturn()).get("slug").asText();
        long versionId = addVersion(id, "v1", "/wf-" + id);
        mvc.perform(as(post("/api/v1/apis/" + id + "/versions/" + versionId + "/targets"), "admin")
                .content(json(Map.of("url", "http://localhost:9001", "weight", 1)))).andExpect(status().isCreated());
        mvc.perform(as(post("/api/v1/apis/" + id + "/lifecycle"), "admin").content(json(Map.of("target", "ACTIVE")))).andExpect(status().isOk());

        // not published without an ACTIVE policy (fail closed)
        assertThat(snapshotRoutes(slug)).isFalse();

        // submit as admin, self-approval is forbidden, approver can approve
        MvcResult sub = mvc.perform(as(post("/api/v1/apis/" + id + "/policies"), "admin")
                .content(json(Map.of("config", goodPolicy(), "comment", "initial")))).andExpect(status().isCreated()).andReturn();
        long pid = body(sub).get("id").asLong();
        mvc.perform(as(post("/api/v1/policies/" + pid + "/approve"), "admin")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("self_approval"));
        mvc.perform(as(post("/api/v1/policies/" + pid + "/approve"), "owner-orders")).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/policies/" + pid + "/approve"), "approver")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(snapshotRoutes(slug)).isTrue();

        // conditional GET: unchanged snapshot returns 304
        MvcResult snap = mvc.perform(get("/internal/v1/gateway/snapshot").header("X-Gateway-Token", GATEWAY_TOKEN))
                .andExpect(status().isOk()).andReturn();
        String etag = snap.getResponse().getHeader("ETag");
        mvc.perform(get("/internal/v1/gateway/snapshot").header("X-Gateway-Token", GATEWAY_TOKEN).header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        // second version then revert to v1 (history is append-only)
        Map<String, Object> v2 = new java.util.HashMap<>(goodPolicy());
        v2.put("rateLimitPerMinute", 500);
        v2.put("burst", 50);
        long pid2 = body(mvc.perform(as(post("/api/v1/apis/" + id + "/policies"), "admin")
                .content(json(Map.of("config", v2)))).andReturn()).get("id").asLong();
        mvc.perform(as(post("/api/v1/policies/" + pid2 + "/approve"), "approver")).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/apis/" + id + "/policies/revert"), "approver").content(json(Map.of("toVersionNo", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(3))
                .andExpect(jsonPath("$.config.rateLimitPerMinute").value(100));
        MvcResult hist = mvc.perform(as(get("/api/v1/apis/" + id + "/policies"), "viewer")).andReturn();
        assertThat(body(hist)).hasSize(3);
        assertThat(body(hist).get(0).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body(hist).get(1).get("status").asText()).isEqualTo("SUPERSEDED");

        // everything above is in the audit trail
        MvcResult audit = mvc.perform(as(get("/api/v1/audit?entityType=API&entityId=" + id + "&size=50"), "admin")).andReturn();
        String actions = audit.getResponse().getContentAsString();
        assertThat(actions).contains("POLICY_SUBMITTED", "POLICY_APPROVED", "POLICY_REVERTED", "API_LIFECYCLE");
    }

    /** Concurrency: simultaneous submissions race on max(version_no)+1; the unique key must turn collisions into 409, never 500 or duplicates. */
    @Test
    void concurrentPolicySubmissionsYieldUniqueVersionsAndNeverServerErrors() throws Exception {
        long id = createApi("admin", "platform");
        String token = token("admin");
        String body = json(Map.of("config", goodPolicy(), "comment", "race"));
        int n = 12;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        var go = new java.util.concurrent.CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/v1/apis/" + id + "/policies").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        for (var f : futures) {
            int status = f.get(60, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(status).isIn(201, 409);
            if (status == 201) created++;
        }
        pool.shutdown();
        assertThat(created).isGreaterThanOrEqualTo(1);
        MvcResult hist = mvc.perform(as(get("/api/v1/apis/" + id + "/policies"), "admin")).andReturn();
        assertThat(body(hist)).hasSize(created); // every 201 persisted exactly one version, no duplicates
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        body(hist).forEach(p -> numbers.add(p.get("versionNo").asInt()));
        assertThat(numbers).hasSize(created);
    }

    boolean snapshotRoutes(String slug) throws Exception {
        MvcResult r = mvc.perform(get("/internal/v1/gateway/snapshot").header("X-Gateway-Token", GATEWAY_TOKEN))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode route : body(r).get("routes")) if (route.get("apiSlug").asText().equals(slug)) return true;
        return false;
    }

    @Test
    void internalEndpointsRequireTheGatewayToken() throws Exception {
        mvc.perform(get("/internal/v1/gateway/snapshot")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/gateway/snapshot").header("X-Gateway-Token", "wrong")).andExpect(status().isUnauthorized());
        // a valid user JWT is NOT enough for the data-plane channel
        mvc.perform(as(get("/internal/v1/gateway/snapshot"), "admin")).andExpect(status().isForbidden());
    }

    // ---------- consumers and keys ----------
    @Test
    void apiKeyIsShownOnceStoredHashedAndAppearsInSnapshotOnlyWhenSubscribed() throws Exception {
        long apiId = createApi("admin", "platform");
        String slug = body(mvc.perform(as(get("/api/v1/apis/" + apiId), "admin")).andReturn()).get("slug").asText();
        long vId = addVersion(apiId, "v1", "/keys-" + apiId);
        mvc.perform(as(post("/api/v1/apis/" + apiId + "/versions/" + vId + "/targets"), "admin")
                .content(json(Map.of("url", "http://localhost:9001", "weight", 1)))).andExpect(status().isCreated());
        mvc.perform(as(post("/api/v1/apis/" + apiId + "/lifecycle"), "admin").content(json(Map.of("target", "ACTIVE")))).andExpect(status().isOk());
        long pid = body(mvc.perform(as(post("/api/v1/apis/" + apiId + "/policies"), "admin")
                .content(json(Map.of("config", goodPolicy())))).andReturn()).get("id").asLong();
        mvc.perform(as(post("/api/v1/policies/" + pid + "/approve"), "approver")).andExpect(status().isOk());

        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        long cid = body(mvc.perform(as(post("/api/v1/consumers"), "admin").content(json(Map.of(
                "name", "C " + clientId, "clientId", clientId, "ownerEmail", "x@example.com")))).andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        MvcResult issued = mvc.perform(as(post("/api/v1/consumers/" + cid + "/keys"), "admin").content(json(Map.of("validDays", 30))))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String apiKey = body(issued).get("apiKey").asText();
        String prefix = body(issued).get("keyPrefix").asText();
        assertThat(apiKey).startsWith(prefix + ".");

        // list view never contains the secret
        MvcResult list = mvc.perform(as(get("/api/v1/consumers/" + cid + "/keys"), "admin")).andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain(apiKey).contains(prefix);

        // no subscription yet -> key not distributed to gateways
        assertThat(snapshotKey(prefix)).isNull();
        mvc.perform(as(post("/api/v1/consumers/" + cid + "/subscriptions"), "admin").content(json(Map.of("apiId", apiId))))
                .andExpect(status().isCreated());
        JsonNode entry = snapshotKey(prefix);
        assertThat(entry).isNotNull();
        assertThat(entry.get("hash").asText()).hasSize(64).isNotEqualTo(apiKey);
        assertThat(entry.get("apiSlugs").get(0).asText()).isEqualTo(slug);

        // duplicate subscription conflicts; revoked keys leave the snapshot
        mvc.perform(as(post("/api/v1/consumers/" + cid + "/subscriptions"), "admin").content(json(Map.of("apiId", apiId))))
                .andExpect(status().isConflict());
        long keyId = body(issued).get("id").asLong();
        mvc.perform(as(delete("/api/v1/consumers/" + cid + "/keys/" + keyId), "admin")).andExpect(status().isNoContent());
        assertThat(snapshotKey(prefix)).isNull();
    }

    JsonNode snapshotKey(String prefix) throws Exception {
        MvcResult r = mvc.perform(get("/internal/v1/gateway/snapshot").header("X-Gateway-Token", GATEWAY_TOKEN)).andReturn();
        for (JsonNode k : body(r).get("keys")) if (k.get("prefix").asText().equals(prefix)) return k;
        return null;
    }

    // ---------- telemetry + incidents ----------
    @Test
    void telemetryIngestionIsIdempotentAndFeedsIncidentRules() throws Exception {
        String batch = "batch-" + UUID.randomUUID();
        String route = "inc-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> bucket = new java.util.HashMap<>();
        bucket.put("route", route); bucket.put("bucketStart", Instant.now().minusSeconds(60).toString());
        bucket.put("requests", 200); bucket.put("errors4xx", 0); bucket.put("errors5xx", 80); bucket.put("rateLimited", 0);
        bucket.put("authFailures", 0); bucket.put("timeouts", 10); bucket.put("latencySumMs", 200 * 2500); bucket.put("latencyMaxMs", 8000);
        Map<String, Object> event = Map.of("ts", Instant.now().toString(), "type", "CIRCUIT_OPEN", "severity", "ERROR",
                "route", route, "message", "opened");
        String payload = json(Map.of("batchId", batch, "buckets", List.of(bucket), "events", List.of(event)));
        mvc.perform(post("/internal/v1/telemetry/batches").header("X-Gateway-Token", GATEWAY_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.storedBuckets").value(1));
        // resend (gateway retry after timeout): acknowledged, not double counted
        mvc.perform(post("/internal/v1/telemetry/batches").header("X-Gateway-Token", GATEWAY_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.storedBuckets").value(0));

        MvcResult summary = mvc.perform(as(get("/api/v1/traffic/summary?windowMinutes=15"), "viewer")).andReturn();
        for (JsonNode r : body(summary).get("routes")) {
            if (r.get("route").asText().equals(route)) assertThat(r.get("requests").asLong()).isEqualTo(200);
        }
        MvcResult inc = mvc.perform(as(get("/api/v1/incidents?windowMinutes=15"), "operator")).andExpect(status().isOk()).andReturn();
        String rules = inc.getResponse().getContentAsString();
        assertThat(rules).contains("HIGH_5XX_RATE", "BACKEND_TIMEOUTS", "ELEVATED_LATENCY", "CIRCUIT_BREAKER_OPEN", "suggestedSteps");
    }

    /** Regression: found by the live smoke run - a batch with several minutes of one route used to violate the unique key. */
    @Test
    void batchWithSeveralMinutesOfTheSameRouteStoresEveryMinute() throws Exception {
        String route = "multi-" + UUID.randomUUID().toString().substring(0, 6);
        java.util.List<Map<String, Object>> buckets = new java.util.ArrayList<>();
        for (int m = 1; m <= 3; m++) {
            Map<String, Object> b = new java.util.HashMap<>();
            b.put("route", route); b.put("bucketStart", Instant.now().minusSeconds(60L * m).toString());
            b.put("requests", 10); b.put("errors4xx", 0); b.put("errors5xx", 0); b.put("rateLimited", 0);
            b.put("authFailures", 0); b.put("timeouts", 0); b.put("latencySumMs", 100); b.put("latencyMaxMs", 20);
            buckets.add(b);
        }
        String payload = json(Map.of("batchId", "batch-" + UUID.randomUUID(), "buckets", buckets, "events", List.of()));
        mvc.perform(post("/internal/v1/telemetry/batches").header("X-Gateway-Token", GATEWAY_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.storedBuckets").value(3));
    }

    @Test
    void malformedTelemetryIsRejected() throws Exception {
        mvc.perform(post("/internal/v1/telemetry/batches").header("X-Gateway-Token", GATEWAY_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"batchId\":\"\",\"buckets\":null}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- graphql / openapi / headers ----------
    @Test
    void graphQlReturnsApisWithVersionsUnderTheSameAuthorization() throws Exception {
        createApi("admin", "platform");
        String q = json(Map.of("query", "{ apis(size: 5) { totalElements content { slug lifecycle versions { label } } } }"));
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(q)).andExpect(status().isUnauthorized());
        // Spring GraphQL on MVC completes asynchronously, so dispatch the async result
        MvcResult started = mvc.perform(as(post("/graphql"), "viewer").content(q)).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apis.content[0].slug").exists()).andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void openApiDocumentIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/apis']").exists());
    }

    @Test
    void correlationIdIsGeneratedValidatedAndReturned() throws Exception {
        MvcResult generated = mvc.perform(as(get("/api/v1/apis"), "viewer")).andExpect(status().isOk()).andReturn();
        assertThat(generated.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
        mvc.perform(as(get("/api/v1/apis"), "viewer").header("X-Correlation-Id", "trace-12345678"))
                .andExpect(header().string("X-Correlation-Id", "trace-12345678"));
        // injection attempt (newline / too short) is replaced, not echoed
        MvcResult bad = mvc.perform(as(get("/api/v1/apis"), "viewer").header("X-Correlation-Id", "x\nFAKE-LOG-LINE")).andReturn();
        assertThat(bad.getResponse().getHeader("X-Correlation-Id")).doesNotContain("FAKE");
    }

    @Test
    void securityHeadersAndOversizedBodiesAreHandled() throws Exception {
        // HSTS is only emitted on HTTPS requests, so MockMvc (plain HTTP) can assert the other headers only
        mvc.perform(as(get("/api/v1/apis"), "viewer")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        byte[] huge = new byte[3 * 1024 * 1024];
        java.util.Arrays.fill(huge, (byte) 'a');
        mvc.perform(as(post("/api/v1/apis"), "admin").content(huge)).andExpect(status().isPayloadTooLarge());
    }
}
