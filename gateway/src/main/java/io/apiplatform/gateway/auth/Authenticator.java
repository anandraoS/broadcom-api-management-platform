package io.apiplatform.gateway.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.apiplatform.gateway.GatewayProperties;
import io.apiplatform.gateway.config.Snapshot;
import io.apiplatform.gateway.config.SnapshotHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Consumer authentication. API keys are looked up by their non-secret prefix and compared by SHA-256 hash in constant
 * time; expiry and subscription to the requested API are enforced from the snapshot. JWTs are verified for signature
 * (HS256 in dev mode; production would verify against the OIDC provider's JWKS), issuer and expiry.
 */
@Component
public class Authenticator {
    public record Result(boolean authenticated, String consumerId, List<String> roles, String failure) {
        static Result ok(String id, List<String> roles) { return new Result(true, id, roles, null); }
        static Result fail(String why) { return new Result(false, null, List.of(), why); }
    }

    private final SnapshotHolder holder;
    private final GatewayProperties props;
    private volatile Map<String, Snapshot.Key> keysByPrefix = Map.of();
    private volatile String keysVersion = "";

    public Authenticator(SnapshotHolder holder, GatewayProperties props) {
        this.holder = holder;
        this.props = props;
    }

    private Map<String, Snapshot.Key> keys() {
        var st = holder.current();
        String v = st.snapshot().version();
        if (!v.equals(keysVersion)) {
            Map<String, Snapshot.Key> m = new HashMap<>();
            for (Snapshot.Key k : st.snapshot().keys()) m.put(k.prefix(), k);
            keysByPrefix = Map.copyOf(m);
            keysVersion = v;
        }
        return keysByPrefix;
    }

    public Result authenticate(String authType, String apiSlug, String apiKeyHeader, String authorizationHeader) {
        return switch (authType) {
            case "NONE" -> Result.ok("anonymous", List.of());
            case "API_KEY" -> apiKey(apiSlug, apiKeyHeader);
            case "JWT" -> jwt(authorizationHeader);
            default -> Result.fail("unsupported auth type"); // fail closed on unknown policy values
        };
    }

    private Result apiKey(String apiSlug, String key) {
        if (key == null || key.isBlank() || key.length() > 200) return Result.fail("missing api key");
        int dot = key.indexOf('.');
        if (dot < 1) return Result.fail("malformed api key");
        Snapshot.Key entry = keys().get(key.substring(0, dot));
        // Hash even when the prefix is unknown so response time does not reveal whether a prefix exists.
        String presented = sha256Hex(key);
        String expected = entry == null ? "0".repeat(64) : entry.hash();
        boolean match = MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        if (entry == null || !match) return Result.fail("invalid api key");
        if (entry.expiresAt() != null && entry.expiresAt().isBefore(Instant.now())) return Result.fail("api key expired");
        if (entry.apiSlugs() == null || !entry.apiSlugs().contains(apiSlug)) return Result.fail("consumer is not subscribed to this api");
        return Result.ok(entry.clientId(), List.of());
    }

    private Result jwt(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return Result.fail("missing bearer token");
        if (props.jwtSecret() == null || props.jwtSecret().length() < 32) return Result.fail("jwt verification not configured");
        try {
            DecodedJWT d = JWT.require(Algorithm.HMAC256(props.jwtSecret())).withIssuer(props.jwtIssuer()).build()
                    .verify(authorization.substring(7));
            List<String> roles = d.getClaim("roles").isNull() ? List.of() : d.getClaim("roles").asList(String.class);
            return Result.ok(d.getSubject(), roles);
        } catch (JWTVerificationException e) {
            return Result.fail("invalid token"); // never echo verifier details to the caller
        }
    }

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
