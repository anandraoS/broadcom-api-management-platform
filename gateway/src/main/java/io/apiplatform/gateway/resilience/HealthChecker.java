package io.apiplatform.gateway.resilience;

import io.apiplatform.gateway.config.Snapshot;
import io.apiplatform.gateway.config.SnapshotHolder;
import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Active health checks; transitions (healthy <-> unhealthy) are emitted as operational events. */
@Component
public class HealthChecker {
    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);
    private final SnapshotHolder holder;
    private final TargetSelector selector;
    private final TelemetryRecorder telemetry;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public HealthChecker(SnapshotHolder holder, TargetSelector selector, TelemetryRecorder telemetry) {
        this.holder = holder;
        this.selector = selector;
        this.telemetry = telemetry;
    }

    @Scheduled(fixedDelayString = "${gateway.health-check-ms:5000}", initialDelayString = "${gateway.health-check-ms:5000}")
    public void checkAll() {
        Set<String> seen = new HashSet<>();
        for (Snapshot.Route r : holder.current().snapshot().routes()) {
            for (Snapshot.Target t : r.targets()) {
                if (!seen.add(t.url())) continue;
                boolean before = selector.isHealthy(t.url());
                boolean now = probe(t);
                selector.setHealthy(t.url(), now);
                if (before && !now) {
                    log.warn("target unhealthy: {}", t.url());
                    telemetry.event("TARGET_UNHEALTHY", "WARN", r.id(), "health check failed for " + t.url(), null);
                } else if (!before && now) {
                    telemetry.event("TARGET_RECOVERED", "INFO", r.id(), "health check recovered for " + t.url(), null);
                }
            }
        }
    }

    boolean probe(Snapshot.Target t) {
        try {
            String path = t.healthPath() == null ? "/actuator/health" : t.healthPath();
            HttpRequest req = HttpRequest.newBuilder(URI.create(t.url().replaceAll("/+$", "") + path))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
