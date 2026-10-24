package io.apiplatform.gateway.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiplatform.gateway.GatewayProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ships aggregated telemetry to the control plane. Delivery is at-least-once: the in-flight batch (with a fixed
 * batchId) is re-sent until acknowledged, and the control plane de-duplicates on (batchId, route). If the control
 * plane is down, new aggregates keep accumulating in memory up to a cap (oldest dropped) - request handling is never
 * affected by telemetry failures.
 */
@Component
public class TelemetryShipper {
    private static final Logger log = LoggerFactory.getLogger(TelemetryShipper.class);
    private static final int MAX_BACKLOG_BUCKETS = 2000;
    private final TelemetryRecorder recorder;
    private final GatewayProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final List<Map<String, Object>> backlogBuckets = new ArrayList<>();
    private final List<Map<String, Object>> backlogEvents = new ArrayList<>();
    private String inflightId;
    private String inflightBody;

    public TelemetryShipper(TelemetryRecorder recorder, GatewayProperties props, ObjectMapper mapper) {
        this.recorder = recorder;
        this.props = props;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${gateway.telemetry-flush-ms:15000}", initialDelayString = "${gateway.telemetry-flush-ms:15000}")
    public synchronized void flush() {
        if (props.controlPlaneUrl() == null || props.controlPlaneUrl().isBlank()) return;
        collect();
        if (inflightBody == null) {
            if (backlogBuckets.isEmpty() && backlogEvents.isEmpty()) return;
            int b = Math.min(backlogBuckets.size(), 500), e = Math.min(backlogEvents.size(), 500);
            Map<String, Object> batch = new LinkedHashMap<>();
            inflightId = props.instanceId() + "-" + UUID.randomUUID();
            batch.put("batchId", inflightId);
            batch.put("buckets", new ArrayList<>(backlogBuckets.subList(0, b)));
            batch.put("events", new ArrayList<>(backlogEvents.subList(0, e)));
            try {
                inflightBody = mapper.writeValueAsString(batch);
            } catch (Exception ex) {
                return;
            }
            backlogBuckets.subList(0, b).clear();
            backlogEvents.subList(0, e).clear();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.controlPlaneUrl() + "/internal/v1/telemetry/batches"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .header("X-Gateway-Token", props.serviceToken() == null ? "" : props.serviceToken())
                    .POST(HttpRequest.BodyPublishers.ofString(inflightBody)).build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() == 202) {
                inflightBody = null;
                inflightId = null;
            } else if (res.statusCode() >= 400 && res.statusCode() < 500) {
                log.warn("control plane rejected telemetry batch {} with {}; dropping it", inflightId, res.statusCode());
                recorder.countDropped();
                inflightBody = null;
                inflightId = null;
            } else {
                log.warn("telemetry flush failed status={}, will retry same batch {}", res.statusCode(), inflightId);
            }
        } catch (Exception ex) {
            log.warn("telemetry flush failed ({}), will retry same batch {}", ex.getClass().getSimpleName(), inflightId);
        }
    }

    private void collect() {
        recorder.drainCompleted().forEach((k, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("route", k.route());
            m.put("bucketStart", k.minute().toString());
            m.put("requests", a.requests.sum());
            m.put("errors4xx", a.e4.sum());
            m.put("errors5xx", a.e5.sum());
            m.put("rateLimited", a.rateLimited.sum());
            m.put("authFailures", a.authFailures.sum());
            m.put("timeouts", a.timeouts.sum());
            m.put("latencySumMs", a.latencySum.sum());
            m.put("latencyMaxMs", a.latencyMax.get());
            backlogBuckets.add(m);
        });
        for (TelemetryRecorder.Event e : recorder.drainEvents(500)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ts", e.ts().toString());
            m.put("type", e.type());
            m.put("severity", e.severity());
            if (e.route() != null && !e.route().equals(TelemetryRecorder.UNMATCHED)) m.put("route", e.route());
            m.put("message", e.message().length() > 480 ? e.message().substring(0, 480) : e.message());
            if (e.correlationId() != null) m.put("correlationId", e.correlationId());
            backlogEvents.add(m);
        }
        while (backlogBuckets.size() > MAX_BACKLOG_BUCKETS) { backlogBuckets.remove(0); recorder.countDropped(); }
        while (backlogEvents.size() > MAX_BACKLOG_BUCKETS) { backlogEvents.remove(0); recorder.countDropped(); }
    }
}
