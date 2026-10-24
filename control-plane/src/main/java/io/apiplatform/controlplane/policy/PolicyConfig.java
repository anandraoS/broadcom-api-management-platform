package io.apiplatform.controlplane.policy;

import java.util.List;
import java.util.Set;

/**
 * Versioned gateway policy. The gateway module has a mirror of this record (contract documented in
 * docs/api-contracts.md and enforced by gateway/src/test/resources/contract/snapshot.json).
 */
public record PolicyConfig(
        AuthType auth,
        String requiredRole,
        Set<String> allowedMethods,
        Integer rateLimitPerMinute,
        Integer burst,
        RateAlgorithm rateAlgorithm,
        Integer timeoutMs,
        Long maxRequestBytes,
        List<String> ipAllowCidrs,
        Boolean retryEnabled) {

    public enum AuthType { NONE, API_KEY, JWT }

    public enum RateAlgorithm { FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET }
}
