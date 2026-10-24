package io.apiplatform.gateway.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * Two sinks per request: Micrometer (Prometheus scrape, percentile histograms) and per-minute aggregates that are
 * shipped to the control plane for the incident view. Cardinality control: the route label is the registered
 * apiSlug/version (bounded) or the constant "unmatched" - never the raw path, consumer or IP.
 */
@Component
public class TelemetryRecorder {
    public static final String UNMATCHED = "unmatched";
    static final int MAX_PENDING_EVENTS = 1000;

    public static final class Agg {
        public final LongAdder requests = new LongAdder(), e4 = new LongAdder(), e5 = new LongAdder(), rateLimited = new LongAdder(),
                authFailures = new LongAdder(), timeouts = new LongAdder(), latencySum = new LongAdder();
        public final java.util.concurrent.atomic.AtomicLong latencyMax = new java.util.concurrent.atomic.AtomicLong();
    }

    public record Key(String route, Instant minute) {}

    public record Event(Instant ts, String type, String severity, String route, String message, String correlationId) {}

    private final MeterRegistry registry;
    private final Map<Key, Agg> aggregates = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final Counter dropped;

    public TelemetryRecorder(MeterRegistry registry) {
        this.registry = registry;
        this.dropped = Counter.builder("gateway.telemetry.dropped").description("telemetry items dropped because buffers were full").register(registry);
    }

    public void request(String route, int status, long latencyMs, boolean rateLimited, boolean authFailure, boolean timeout) {
        Timer.builder("gateway.requests").description("gateway request latency")
                .tag("route", route).tag("status_class", (status / 100) + "xx")
                .publishPercentileHistogram().register(registry).record(Duration.ofMillis(latencyMs));
        if (rateLimited) registry.counter("gateway.rate_limited", "route", route).increment();
        if (authFailure) registry.counter("gateway.auth_failures", "route", route).increment();
        if (timeout) registry.counter("gateway.backend.timeouts", "route", route).increment();

        Agg a = aggregates.computeIfAbsent(new Key(route, Instant.now().truncatedTo(ChronoUnit.MINUTES)), k -> new Agg());
        a.requests.increment();
        if (status >= 400 && status < 500) a.e4.increment();
        if (status >= 500) a.e5.increment();
        if (rateLimited) a.rateLimited.increment();
        if (authFailure) a.authFailures.increment();
        if (timeout) a.timeouts.increment();
        a.latencySum.add(latencyMs);
        a.latencyMax.accumulateAndGet(latencyMs, Math::max);
    }

    public void retry(String route) { registry.counter("gateway.retries", "route", route).increment(); }

    /** Bounded buffer: when full the oldest event is dropped - telemetry must never block or fail a request. */
    public void event(String type, String severity, String route, String message, String correlationId) {
        events.add(new Event(Instant.now(), type, severity, route, message, correlationId));
        if (eventCount.incrementAndGet() > MAX_PENDING_EVENTS) {
            if (events.poll() != null) eventCount.decrementAndGet();
            dropped.increment();
        }
    }

    /** Removes and returns aggregates for COMPLETED minutes only. */
    public Map<Key, Agg> drainCompleted() {
        Instant current = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Map<Key, Agg> out = new java.util.HashMap<>();
        for (Iterator<Map.Entry<Key, Agg>> it = aggregates.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getKey().minute().isBefore(current)) {
                out.put(e.getKey(), e.getValue());
                it.remove();
            }
        }
        return out;
    }

    public List<Event> drainEvents(int max) {
        List<Event> out = new ArrayList<>();
        Event e;
        while (out.size() < max && (e = events.poll()) != null) {
            eventCount.decrementAndGet();
            out.add(e);
        }
        return out;
    }

    public void countDropped() { dropped.increment(); }
}
