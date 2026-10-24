package io.apiplatform.controlplane.policy;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Pure, side-effect-free validation so it can be unit tested and reused for dry-run validation. */
@Component
public class PolicyValidator {
    static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    public static final long MAX_REQUEST_BYTES = 10L * 1024 * 1024;
    /** 100,000 requests/second per consumer per route: an upper sanity bound, not a recommendation. */
    public static final int MAX_RATE_PER_MINUTE = 6_000_000;

    public List<String> validate(PolicyConfig c) {
        List<String> errors = new ArrayList<>();
        if (c == null) return List.of("policy body is required");
        if (c.auth() == null) errors.add("auth is required (NONE, API_KEY or JWT)");
        if (c.allowedMethods() == null || c.allowedMethods().isEmpty()) {
            errors.add("allowedMethods must contain at least one method");
        } else {
            for (String m : c.allowedMethods()) {
                if (!METHODS.contains(m)) errors.add("unsupported method: " + m);
            }
        }
        // Safety rule: unauthenticated policies may only expose read-only methods.
        if (c.auth() == PolicyConfig.AuthType.NONE && c.allowedMethods() != null
                && c.allowedMethods().stream().anyMatch(m -> !SAFE_METHODS.contains(m))) {
            errors.add("auth=NONE is only allowed with read-only methods (GET, HEAD, OPTIONS)");
        }
        if (c.rateLimitPerMinute() == null || c.rateLimitPerMinute() < 1 || c.rateLimitPerMinute() > MAX_RATE_PER_MINUTE) {
            errors.add("rateLimitPerMinute must be between 1 and " + MAX_RATE_PER_MINUTE);
        }
        if (c.rateAlgorithm() == null) errors.add("rateAlgorithm is required");
        if (c.burst() == null || c.burst() < 1) {
            errors.add("burst must be >= 1");
        } else if (c.rateLimitPerMinute() != null && c.burst() > c.rateLimitPerMinute() * 10L) {
            errors.add("burst must not exceed 10x rateLimitPerMinute");
        }
        if (c.timeoutMs() == null || c.timeoutMs() < 100 || c.timeoutMs() > 60_000) {
            errors.add("timeoutMs must be between 100 and 60000");
        }
        if (c.maxRequestBytes() == null || c.maxRequestBytes() < 0 || c.maxRequestBytes() > MAX_REQUEST_BYTES) {
            errors.add("maxRequestBytes must be between 0 and " + MAX_REQUEST_BYTES);
        }
        if (c.retryEnabled() == null) errors.add("retryEnabled is required");
        if (c.ipAllowCidrs() != null) {
            for (String cidr : c.ipAllowCidrs()) {
                if (!validCidr(cidr)) errors.add("invalid CIDR: " + cidr);
                else if (cidr.equals("0.0.0.0/0")) errors.add("0.0.0.0/0 is not a restriction; omit ipAllowCidrs instead");
            }
        }
        if (c.requiredRole() != null && !c.requiredRole().matches("[A-Za-z0-9_:.-]{1,64}")) {
            errors.add("requiredRole contains invalid characters");
        }
        if (c.requiredRole() != null && c.auth() == PolicyConfig.AuthType.NONE) {
            errors.add("requiredRole needs auth=API_KEY or JWT");
        }
        return errors;
    }

    static boolean validCidr(String cidr) {
        if (cidr == null) return false;
        String[] p = cidr.split("/");
        if (p.length != 2) return false;
        try {
            if (!p[0].matches("[0-9.]+")) return false; // IPv4 literals only; avoids DNS lookups during validation
            InetAddress.getByName(p[0]);
            int bits = Integer.parseInt(p[1]);
            return bits >= 0 && bits <= 32;
        } catch (Exception e) {
            return false;
        }
    }
}
