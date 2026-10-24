package io.apiplatform.gateway.resilience;

import java.util.function.LongSupplier;

/**
 * Consecutive-failure circuit breaker with a single half-open probe. Chosen over a sliding failure-rate window for
 * simplicity and predictability at this scale; a rate-based breaker (e.g. Resilience4j) is the documented upgrade
 * path (docs/adr/0006-resilience-primitives.md).
 */
public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMs;
    private final LongSupplier clock;
    private State state = State.CLOSED;
    private int failures;
    private long openedAt;
    private boolean probeInFlight;

    public CircuitBreaker(int failureThreshold, long openMs, LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.openMs = openMs;
        this.clock = clock;
    }

    /** @return true if a call may proceed (and, in HALF_OPEN, reserves the single probe slot). */
    public synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (clock.getAsLong() - openedAt >= openMs) {
                    state = State.HALF_OPEN;
                    probeInFlight = true;
                    return true;
                }
                return false;
            default: // HALF_OPEN
                if (probeInFlight) return false;
                probeInFlight = true;
                return true;
        }
    }

    /** Non-mutating check used for candidate filtering before tryAcquire() reserves the call. */
    public synchronized boolean canAttempt() {
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> clock.getAsLong() - openedAt >= openMs;
            case HALF_OPEN -> !probeInFlight;
        };
    }

    public synchronized void onSuccess() {
        failures = 0;
        probeInFlight = false;
        state = State.CLOSED;
    }

    /** @return true if this failure transitioned the breaker to OPEN. */
    public synchronized boolean onFailure() {
        probeInFlight = false;
        if (state == State.HALF_OPEN) {
            open();
            return true;
        }
        if (++failures >= failureThreshold && state == State.CLOSED) {
            open();
            return true;
        }
        return false;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.getAsLong();
        failures = 0;
    }

    public synchronized State state() { return state; }
}
