package io.apiplatform.gateway.resilience;

import io.apiplatform.gateway.GatewayProperties;
import io.apiplatform.gateway.config.Snapshot;
import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Chooses a backend target for a route: weighted, skipping targets that failed health checks or whose circuit is open.
 * "Smooth weighted round robin" keeps the distribution proportional to weights without bursts to one target.
 */
@Component
public class TargetSelector {
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> healthy = new ConcurrentHashMap<>();
    private final Map<String, int[]> currentWeights = new ConcurrentHashMap<>();
    private final GatewayProperties props;
    private final TelemetryRecorder telemetry;

    public TargetSelector(GatewayProperties props, TelemetryRecorder telemetry) {
        this.props = props;
        this.telemetry = telemetry;
    }

    public CircuitBreaker breaker(String targetUrl) {
        return breakers.computeIfAbsent(targetUrl,
                u -> new CircuitBreaker(props.breakerFailureThreshold(), props.breakerOpenMs(), System::currentTimeMillis));
    }

    public void setHealthy(String url, boolean h) { healthy.put(url, h); }

    public boolean isHealthy(String url) { return healthy.getOrDefault(url, true); }

    /** Returns the chosen target, or empty when every target is unhealthy/open (caller degrades gracefully with 503). */
    public Optional<Snapshot.Target> select(Snapshot.Route route, Set<String> exclude) {
        List<Snapshot.Target> ts = route.targets();
        Set<String> skip = new HashSet<>(exclude);
        while (true) {
            Snapshot.Target pick = pick(route, ts, skip);
            if (pick == null) return Optional.empty();
            if (breaker(pick.url()).tryAcquire()) return Optional.of(pick);
            skip.add(pick.url()); // lost a half-open probe race; try the others
        }
    }

    private Snapshot.Target pick(Snapshot.Route route, List<Snapshot.Target> ts, Set<String> skip) {
        int[] cw = currentWeights.computeIfAbsent(route.id() + "#" + ts.size(), k -> new int[ts.size()]);
        synchronized (cw) {
            int total = 0, bestIdx = -1;
            for (int i = 0; i < ts.size(); i++) {
                Snapshot.Target t = ts.get(i);
                if (skip.contains(t.url()) || !isHealthy(t.url()) || !breaker(t.url()).canAttempt()) continue;
                int w = Math.max(1, t.weight());
                cw[i] += w;
                total += w;
                if (bestIdx < 0 || cw[i] > cw[bestIdx]) bestIdx = i;
            }
            if (bestIdx < 0) return null;
            cw[bestIdx] -= total;
            return ts.get(bestIdx);
        }
    }

    public void success(String url) { breaker(url).onSuccess(); }

    public void failure(String route, String url, String correlationId) {
        if (breaker(url).onFailure()) {
            telemetry.event("CIRCUIT_OPEN", "ERROR", route, "circuit opened for target " + url, correlationId);
        }
    }
}
