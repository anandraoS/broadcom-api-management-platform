package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "traffic_bucket")
public class TrafficBucket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_id", nullable = false) private String batchId;
    @Column(name = "route_slug", nullable = false) private String routeSlug;
    @Column(name = "bucket_start", nullable = false) private Instant bucketStart;
    private long requests;
    @Column(name = "errors_4xx") private long errors4xx;
    @Column(name = "errors_5xx") private long errors5xx;
    @Column(name = "rate_limited") private long rateLimited;
    @Column(name = "auth_failures") private long authFailures;
    private long timeouts;
    @Column(name = "latency_sum_ms") private long latencySumMs;
    @Column(name = "latency_max_ms") private long latencyMaxMs;

    protected TrafficBucket() {}
    public TrafficBucket(String batchId, String routeSlug, Instant bucketStart, long requests, long e4, long e5,
                         long rl, long auth, long timeouts, long latencySum, long latencyMax) {
        this.batchId = batchId; this.routeSlug = routeSlug; this.bucketStart = bucketStart; this.requests = requests;
        this.errors4xx = e4; this.errors5xx = e5; this.rateLimited = rl; this.authFailures = auth;
        this.timeouts = timeouts; this.latencySumMs = latencySum; this.latencyMaxMs = latencyMax;
    }
    public String getBatchId() { return batchId; }
    public String getRouteSlug() { return routeSlug; }
    public Instant getBucketStart() { return bucketStart; }
    public long getRequests() { return requests; }
    public long getErrors4xx() { return errors4xx; }
    public long getErrors5xx() { return errors5xx; }
    public long getRateLimited() { return rateLimited; }
    public long getAuthFailures() { return authFailures; }
    public long getTimeouts() { return timeouts; }
    public long getLatencySumMs() { return latencySumMs; }
    public long getLatencyMaxMs() { return latencyMaxMs; }
}
