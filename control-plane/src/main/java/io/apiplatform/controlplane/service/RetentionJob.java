package io.apiplatform.controlplane.service;

import io.apiplatform.controlplane.repo.IdempotencyRecordRepository;
import io.apiplatform.controlplane.repo.OperationalEventRepository;
import io.apiplatform.controlplane.repo.TrafficBucketRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention for high-volume telemetry tables. audit_event is deliberately NOT purged here: audit retention is a
 * compliance decision (archive to cold storage, then partition-drop) documented in docs/database.md.
 */
@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private final TrafficBucketRepository buckets;
    private final OperationalEventRepository events;
    private final IdempotencyRecordRepository idem;
    private final int telemetryDays;

    public RetentionJob(TrafficBucketRepository buckets, OperationalEventRepository events,
                        IdempotencyRecordRepository idem, @Value("${app.retention.telemetry-days:7}") int telemetryDays) {
        this.buckets = buckets; this.events = events; this.idem = idem; this.telemetryDays = telemetryDays;
    }

    @Scheduled(cron = "${app.retention.cron:0 17 3 * * *}")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(telemetryDays));
        int b = buckets.deleteOlderThan(cutoff);
        int e = events.deleteOlderThan(cutoff);
        int i = idem.deleteOlderThan(Instant.now().minus(Duration.ofHours(24)));
        log.info("retention purge buckets={} events={} idempotencyRecords={}", b, e, i);
    }
}
