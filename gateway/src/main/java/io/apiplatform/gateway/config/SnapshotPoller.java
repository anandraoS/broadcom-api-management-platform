package io.apiplatform.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiplatform.gateway.GatewayProperties;
import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pull-based configuration propagation (see docs/policy-propagation.md):
 *  - polls the control plane with a conditional GET (If-None-Match), so unchanged config costs one 304;
 *  - validates every route and target BEFORE swapping (an invalid snapshot never replaces a good one);
 *  - on any failure keeps serving the last-known-good snapshot and emits SNAPSHOT_STALE after repeated failures.
 * Worst-case propagation delay is roughly snapshotPollMs + control-plane cache invalidation (<= 30s) - bounded, not instant.
 */
@Component
public class SnapshotPoller {
    private static final Logger log = LoggerFactory.getLogger(SnapshotPoller.class);
    private final GatewayProperties props;
    private final SnapshotHolder holder;
    private final DestinationGuard guard;
    private final ObjectMapper mapper;
    private final TelemetryRecorder telemetry;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private String etag;
    private int consecutiveFailures;

    public SnapshotPoller(GatewayProperties props, SnapshotHolder holder, DestinationGuard guard, ObjectMapper mapper,
                          TelemetryRecorder telemetry, MeterRegistry registry) {
        this.props = props; this.holder = holder; this.guard = guard; this.mapper = mapper; this.telemetry = telemetry;
        Gauge.builder("gateway.snapshot.age.seconds", holder,
                h -> h.everLoaded() ? Duration.between(h.current().appliedAt(), Instant.now()).toSeconds() : -1)
                .description("seconds since the last successful snapshot refresh (-1 = never loaded)").register(registry);
    }

    @Scheduled(fixedDelayString = "${gateway.snapshot-poll-ms:5000}")
    public void poll() {
        if (props.controlPlaneUrl() == null || props.controlPlaneUrl().isBlank()) return;
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.controlPlaneUrl() + "/internal/v1/gateway/snapshot"))
                    .timeout(Duration.ofSeconds(5)).header("X-Gateway-Token", props.serviceToken() == null ? "" : props.serviceToken());
            if (etag != null) rb.header("If-None-Match", etag);
            HttpResponse<String> res = http.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 304) {
                markHealthy();
            } else if (res.statusCode() == 200) {
                Snapshot s = mapper.readValue(res.body(), Snapshot.class);
                if (applyValidated(s)) {
                    etag = res.headers().firstValue("ETag").orElse(null);
                }
                markHealthy();
            } else {
                failure("control plane returned status " + res.statusCode());
            }
        } catch (Exception e) {
            failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Package-visible for tests: validate then swap atomically; returns false if the snapshot was rejected. */
    public boolean applyValidated(Snapshot s) {
        if (s == null || s.routes() == null) {
            telemetry.event("INVALID_CONFIG", "ERROR", null, "snapshot rejected: missing routes", null);
            return false;
        }
        List<Snapshot.Route> accepted = new ArrayList<>();
        for (Snapshot.Route r : s.routes()) {
            String problem = problem(r);
            if (problem == null) {
                accepted.add(r);
            } else {
                log.warn("rejecting route {}: {}", r.basePath(), problem);
                telemetry.event("INVALID_CONFIG", "ERROR", r.id(), "route rejected: " + problem, null);
            }
        }
        // Invariant: a snapshot that would silently drop EVERY previously served route is treated as suspect only
        // if it came with rejections; an intentionally empty snapshot (all APIs deactivated) is applied.
        if (accepted.isEmpty() && !s.routes().isEmpty()) {
            telemetry.event("INVALID_CONFIG", "ERROR", null, "snapshot rejected: no valid routes; keeping previous config", null);
            return false;
        }
        Snapshot applied = new Snapshot(s.version(), s.generatedAt(), accepted, s.keys() == null ? List.of() : s.keys());
        holder.apply(applied, new RouteTable(accepted, s.version()));
        log.info("applied snapshot version={} routes={}", s.version(), accepted.size());
        persist(applied);
        return true;
    }

    /** Loads the last-known-good snapshot from disk at startup so a restart during a control-plane outage still serves. */
    @jakarta.annotation.PostConstruct
    void loadFromDisk() {
        String file = props.snapshotCacheFile();
        if (file == null || file.isBlank()) return;
        Path p = Path.of(file);
        if (!Files.isReadable(p)) return;
        try {
            Snapshot s = mapper.readValue(Files.readString(p), Snapshot.class);
            if (applyValidated(s)) log.info("loaded cached snapshot version={} from {}", s.version(), file);
        } catch (Exception e) {
            log.warn("ignoring unreadable snapshot cache {}: {}", file, e.getClass().getSimpleName());
        }
    }

    private void persist(Snapshot s) {
        String file = props.snapshotCacheFile();
        if (file == null || file.isBlank()) return;
        try {
            Path target = Path.of(file);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(s));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.warn("could not persist snapshot cache: {}", e.getClass().getSimpleName());
        }
    }

    private String problem(Snapshot.Route r) {
        if (r.basePath() == null || !r.basePath().matches("/[a-z0-9][a-z0-9/_-]{0,100}")) return "invalid basePath";
        if (r.policy() == null) return "missing policy";
        var p = r.policy();
        if (p.allowedMethods() == null || p.allowedMethods().isEmpty() || p.rateLimitPerMinute() == null || p.rateLimitPerMinute() < 1
                || p.timeoutMs() == null || p.timeoutMs() < 1 || p.auth() == null) return "incomplete policy";
        if (r.targets() == null || r.targets().isEmpty()) return "no targets";
        for (Snapshot.Target t : r.targets()) {
            if (!guard.hostAllowed(t.url())) return "target not in gateway allowlist: " + t.url();
        }
        return null;
    }

    private void markHealthy() { consecutiveFailures = 0; }

    private void failure(String why) {
        consecutiveFailures++;
        log.warn("snapshot refresh failed ({}x): {}; serving last-known-good", consecutiveFailures, why);
        if (consecutiveFailures == 3) {
            telemetry.event("SNAPSHOT_STALE", "WARN", null, "control plane unreachable; serving last-known-good config", null);
        }
    }
}
