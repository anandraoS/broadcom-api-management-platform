package io.apiplatform.gateway.config;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Holds the last-known-good snapshot. Readers get an immutable RouteTable via a volatile read (no locks on the
 * request path). Updates swap the whole structure atomically, so a request never sees a half-applied configuration.
 */
@Component
public class SnapshotHolder {
    public record State(Snapshot snapshot, RouteTable routes, Instant appliedAt) {}

    private final AtomicReference<State> state = new AtomicReference<>(
            new State(new Snapshot("none", Instant.EPOCH, java.util.List.of(), java.util.List.of()), RouteTable.empty(), Instant.EPOCH));

    public State current() { return state.get(); }

    public void apply(Snapshot s, RouteTable table) {
        state.set(new State(s, table, Instant.now()));
    }

    public boolean everLoaded() { return state.get().appliedAt().isAfter(Instant.EPOCH); }
}
