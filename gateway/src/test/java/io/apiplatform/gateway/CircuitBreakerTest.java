package io.apiplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.gateway.resilience.CircuitBreaker;
import io.apiplatform.gateway.resilience.CircuitBreaker.State;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final CircuitBreaker cb = new CircuitBreaker(3, 10_000, now::get);

    @Test
    void opensAfterConsecutiveFailuresAndFailsFast() {
        assertThat(cb.onFailure()).isFalse();
        assertThat(cb.onFailure()).isFalse();
        assertThat(cb.onFailure()).isTrue();
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.tryAcquire()).isFalse();
    }

    @Test
    void successResetsTheFailureCount() {
        cb.onFailure(); cb.onFailure();
        cb.onSuccess();
        cb.onFailure(); cb.onFailure();
        assertThat(cb.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void halfOpenAllowsExactlyOneProbe() {
        for (int i = 0; i < 3; i++) cb.onFailure();
        now.addAndGet(10_000);
        assertThat(cb.canAttempt()).isTrue();
        assertThat(cb.tryAcquire()).isTrue();   // probe
        assertThat(cb.tryAcquire()).isFalse();  // concurrent callers are still rejected
        assertThat(cb.canAttempt()).isFalse();
    }

    @Test
    void successfulProbeClosesAndFailedProbeReopens() {
        for (int i = 0; i < 3; i++) cb.onFailure();
        now.addAndGet(10_000);
        cb.tryAcquire();
        cb.onSuccess();
        assertThat(cb.state()).isEqualTo(State.CLOSED);

        for (int i = 0; i < 3; i++) cb.onFailure();
        now.addAndGet(10_000);
        cb.tryAcquire();
        assertThat(cb.onFailure()).isTrue();
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.tryAcquire()).isFalse();
    }
}
