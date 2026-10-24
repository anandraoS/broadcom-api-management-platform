package io.apiplatform.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.controlplane.policy.PolicyConfig;
import io.apiplatform.controlplane.policy.PolicyConfig.AuthType;
import io.apiplatform.controlplane.policy.PolicyConfig.RateAlgorithm;
import io.apiplatform.controlplane.policy.PolicyValidator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyValidatorTest {
    private final PolicyValidator validator = new PolicyValidator();

    private PolicyConfig valid() {
        return new PolicyConfig(AuthType.API_KEY, null, Set.of("GET", "POST"), 100, 20, RateAlgorithm.TOKEN_BUCKET,
                3000, 1024L, null, true);
    }

    @Test
    void validPolicyHasNoErrors() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void nullBodyIsRejected() {
        assertThat(validator.validate(null)).containsExactly("policy body is required");
    }

    @Test
    void unauthenticatedWritesAreRejected() {
        var c = new PolicyConfig(AuthType.NONE, null, Set.of("GET", "POST"), 100, 20, RateAlgorithm.FIXED_WINDOW, 3000, 1024L, null, false);
        assertThat(validator.validate(c)).anyMatch(e -> e.contains("auth=NONE is only allowed with read-only"));
    }

    @Test
    void unauthenticatedReadsAreAllowed() {
        var c = new PolicyConfig(AuthType.NONE, null, Set.of("GET", "HEAD"), 100, 20, RateAlgorithm.FIXED_WINDOW, 3000, 1024L, null, false);
        assertThat(validator.validate(c)).isEmpty();
    }

    @Test
    void outOfRangeNumbersAreRejected() {
        var c = new PolicyConfig(AuthType.JWT, null, Set.of("GET"), 0, 0, RateAlgorithm.FIXED_WINDOW, 5, -1L, null, true);
        assertThat(validator.validate(c)).hasSize(4);
    }

    @Test
    void burstCannotExceedTenTimesRate() {
        var c = new PolicyConfig(AuthType.JWT, null, Set.of("GET"), 10, 101, RateAlgorithm.TOKEN_BUCKET, 1000, 100L, null, true);
        assertThat(validator.validate(c)).anyMatch(e -> e.contains("burst must not exceed"));
    }

    @Test
    void invalidAndMatchAllCidrsAreRejected() {
        var c = new PolicyConfig(AuthType.JWT, null, Set.of("GET"), 10, 10, RateAlgorithm.TOKEN_BUCKET, 1000, 100L,
                List.of("10.0.0.0/8", "999.1.1.1/8", "0.0.0.0/0", "evil.example.com/8"), true);
        assertThat(validator.validate(c)).hasSize(3);
    }

    @Test
    void unknownMethodIsRejected() {
        var c = new PolicyConfig(AuthType.JWT, null, Set.of("TRACE"), 10, 10, RateAlgorithm.TOKEN_BUCKET, 1000, 100L, null, true);
        assertThat(validator.validate(c)).contains("unsupported method: TRACE");
    }

    @Test
    void requiredRoleNeedsAuthentication() {
        var c = new PolicyConfig(AuthType.NONE, "admin", Set.of("GET"), 10, 10, RateAlgorithm.TOKEN_BUCKET, 1000, 100L, null, true);
        assertThat(validator.validate(c)).anyMatch(e -> e.contains("requiredRole needs auth"));
    }

    @Test
    void requestSizeIsCapped() {
        var c = new PolicyConfig(AuthType.JWT, null, Set.of("POST"), 10, 10, RateAlgorithm.TOKEN_BUCKET, 1000,
                PolicyValidator.MAX_REQUEST_BYTES + 1, null, true);
        assertThat(validator.validate(c)).anyMatch(e -> e.startsWith("maxRequestBytes"));
    }
}
