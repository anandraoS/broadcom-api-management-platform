package io.apiplatform.controlplane.service;

import io.apiplatform.controlplane.domain.OperationalEvent;
import io.apiplatform.controlplane.domain.TrafficBucket;
import io.apiplatform.controlplane.repo.OperationalEventRepository;
import io.apiplatform.controlplane.repo.TrafficBucketRepository;
import io.apiplatform.controlplane.web.Dtos.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingests aggregated gateway telemetry. Gateways may resend a batch after a timeout, so ingestion is idempotent
 * on (batchId, route, minute): a duplicate is acknowledged and ignored (at-least-once delivery, effectively-once storage).
 */
@Service
public class TelemetryService {
    private final TrafficBucketRepository buckets;
    private final OperationalEventRepository eventsRepo;

    public TelemetryService(TrafficBucketRepository buckets, OperationalEventRepository eventsRepo) {
        this.buckets = buckets;
        this.eventsRepo = eventsRepo;
    }

    @Transactional
    public int ingest(TelemetryBatch batch) {
        int stored = 0;
        for (BucketIn b : batch.buckets()) {
            // a batch may hold several minutes of the same route, so the natural key is (batch, route, minute).
            // Normalise to the minute: the column has millisecond precision, so an un-truncated instant would never
            // match its stored value and a replayed batch would hit the unique key instead of being de-duplicated.
            Instant minute = b.bucketStart().truncatedTo(ChronoUnit.MINUTES);
            if (buckets.existsByBatchIdAndRouteSlugAndBucketStart(batch.batchId(), b.route(), minute)) continue;
            buckets.save(new TrafficBucket(batch.batchId(), b.route(), minute, b.requests(), b.errors4xx(),
                    b.errors5xx(), b.rateLimited(), b.authFailures(), b.timeouts(), b.latencySumMs(), b.latencyMaxMs()));
            stored++;
        }
        if (stored > 0 || batch.buckets().isEmpty()) {
            for (EventIn e : batch.events()) {
                eventsRepo.save(new OperationalEvent(e.ts(), e.type(), e.severity(), e.route(), e.message(), e.correlationId()));
            }
        }
        return stored;
    }

    @Transactional(readOnly = true)
    public TrafficSummary summary(int windowMinutes) {
        Instant since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (TrafficBucket b : buckets.findByBucketStartAfter(since)) {
            long[] a = agg.computeIfAbsent(b.getRouteSlug(), k -> new long[9]);
            a[0] += b.getRequests(); a[1] += b.getErrors4xx(); a[2] += b.getErrors5xx(); a[3] += b.getRateLimited();
            a[4] += b.getAuthFailures(); a[5] += b.getTimeouts(); a[6] += b.getLatencySumMs();
            a[7] = Math.max(a[7], b.getLatencyMaxMs());
        }
        List<TrafficRouteSummary> routes = agg.entrySet().stream().map(e -> {
            long[] a = e.getValue();
            double errRate = a[0] == 0 ? 0 : (double) a[2] / a[0];
            double avg = a[0] == 0 ? 0 : (double) a[6] / a[0];
            return new TrafficRouteSummary(e.getKey(), a[0], a[1], a[2], errRate, avg, a[7], a[3], a[4], a[5]);
        }).sorted(Comparator.comparingLong(TrafficRouteSummary::requests).reversed()).toList();
        long total = routes.stream().mapToLong(TrafficRouteSummary::requests).sum();
        long errs = routes.stream().mapToLong(TrafficRouteSummary::errors5xx).sum();
        return new TrafficSummary(windowMinutes, total, total == 0 ? 0 : (double) errs / total, routes);
    }

    @Transactional(readOnly = true)
    public PageView<EventView> events(String type, String route, String severity, Pageable pageable) {
        Page<OperationalEvent> p = eventsRepo.search(nz(type), nz(route), nz(severity), pageable);
        return PageView.of(p.map(TelemetryService::view));
    }

    static EventView view(OperationalEvent e) {
        return new EventView(e.getId(), e.getTs(), e.getType(), e.getSeverity(), e.getRouteSlug(), e.getMessage(), e.getCorrelationId());
    }

    private static String nz(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
