package io.apiplatform.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.controlplane.policy.DestinationProperties;
import io.apiplatform.controlplane.policy.DestinationValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SSRF regression tests: every URL here is a technique an attacker could use to reach internal resources. */
class DestinationValidatorTest {
    private final DestinationValidator v = new DestinationValidator(
            new DestinationProperties(List.of("localhost", "*.internal.example.com"), true));

    @Test
    void allowlistedLoopbackIsAcceptedWhenEnabled() {
        assertThat(v.validate("http://localhost:9001")).isEmpty();
    }

    @Test
    void cloudMetadataAddressIsBlockedEvenIfHostWereAllowlisted() {
        var strict = new DestinationValidator(new DestinationProperties(List.of("169.254.169.254"), true));
        assertThat(strict.validate("http://169.254.169.254")).anyMatch(e -> e.contains("blocked address range"));
    }

    @Test
    void hostsOutsideAllowlistAreRejected() {
        assertThat(v.validate("http://evil.example.org")).anyMatch(e -> e.contains("not in the destination allowlist"));
    }

    @Test
    void nonHttpSchemesAreRejected() {
        assertThat(v.validate("file:///etc/passwd")).anyMatch(e -> e.contains("scheme"));
        assertThat(v.validate("gopher://localhost")).anyMatch(e -> e.contains("scheme"));
    }

    @Test
    void credentialsInUrlAreRejected() {
        assertThat(v.validate("http://user:pass@localhost:9001")).anyMatch(e -> e.contains("credentials"));
    }

    @Test
    void userinfoHostConfusionDoesNotBypassAllowlist() {
        // looks like localhost to a human, but the real host is evil.example.org
        assertThat(v.validate("http://localhost@evil.example.org")).isNotEmpty();
    }

    @Test
    void loopbackIsRejectedWhenDisabled() {
        var prod = new DestinationValidator(new DestinationProperties(List.of("localhost"), false));
        assertThat(prod.validate("http://localhost:9001")).anyMatch(e -> e.contains("loopback"));
    }

    @Test
    void queryAndFragmentAreRejected() {
        assertThat(v.validate("http://localhost:9001?x=1")).anyMatch(e -> e.contains("query"));
    }

    @Test
    void malformedUrlsAreRejected() {
        assertThat(v.validate("http://exa mple.com")).isNotEmpty();
        assertThat(v.validate("not a url")).isNotEmpty();
    }

    @Test
    void anyLocalAddressIsBlocked() {
        var strict = new DestinationValidator(new DestinationProperties(List.of("0.0.0.0"), true));
        assertThat(strict.validate("http://0.0.0.0:80")).anyMatch(e -> e.contains("blocked address range"));
    }
}
