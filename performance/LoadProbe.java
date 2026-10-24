import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Dependency-free closed-loop load probe (single file, run with: java performance/LoadProbe.java ...).
 * A LIGHTWEIGHT SUBSTITUTE for k6/Gatling for machines where those are not installed: it reports measured
 * throughput, latency percentiles and status-code counts - it does NOT replace the k6 scripts in performance/k6.
 * Limitations: client and system under test usually share one machine (CPU contention), closed-loop load
 * (coordinated omission: a slow server lowers the offered rate), no think time, JDK HttpClient (HTTP/1.1).
 *
 * Usage: java performance/LoadProbe.java <url> <concurrency> <seconds> [headerName:value ...]
 */
public class LoadProbe {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        int concurrency = Integer.parseInt(args[1]);
        int seconds = Integer.parseInt(args[2]);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
        for (int i = 3; i < args.length; i++) {
            int c = args[i].indexOf(':');
            rb.header(args[i].substring(0, c), args[i].substring(c + 1));
        }
        HttpRequest request = rb.build();

        // warm-up so JIT and connection pools do not pollute the measured window
        long warmEnd = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < concurrency; t++) pool.submit(() -> {
                while (System.nanoTime() < warmEnd) {
                    try { client.send(request, HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { }
                }
            });
        }

        int max = 5_000_000;
        long[] latenciesUs = new long[max];
        AtomicInteger idx = new AtomicInteger();
        Map<String, LongAdder> statuses = new ConcurrentHashMap<>();
        AtomicLong errors = new AtomicLong();
        long start = System.nanoTime();
        long end = start + Duration.ofSeconds(seconds).toNanos();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < concurrency; t++) pool.submit(() -> {
                while (System.nanoTime() < end) {
                    long t0 = System.nanoTime();
                    try {
                        HttpResponse<Void> r = client.send(request, HttpResponse.BodyHandlers.discarding());
                        long us = (System.nanoTime() - t0) / 1000;
                        int i = idx.getAndIncrement();
                        if (i < max) latenciesUs[i] = us;
                        statuses.computeIfAbsent(String.valueOf(r.statusCode()), k -> new LongAdder()).increment();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            });
        }
        double elapsed = (System.nanoTime() - start) / 1e9;
        int n = Math.min(idx.get(), max);
        long[] sorted = Arrays.copyOf(latenciesUs, n);
        Arrays.sort(sorted);
        Runtime rt = Runtime.getRuntime();
        System.out.printf("url=%s concurrency=%d duration=%.1fs%n", url, concurrency, elapsed);
        System.out.printf("requests=%d throughput=%.0f req/s transportErrors=%d%n", n, n / elapsed, errors.get());
        if (n > 0) {
            System.out.printf("latency ms: p50=%.2f p90=%.2f p95=%.2f p99=%.2f max=%.2f%n", pct(sorted, 50), pct(sorted, 90),
                    pct(sorted, 95), pct(sorted, 99), sorted[n - 1] / 1000.0);
        }
        Map<String, Long> sc = new TreeMap<>();
        statuses.forEach((k, v) -> sc.put(k, v.sum()));
        System.out.println("status counts: " + sc);
        System.out.printf("client: availableProcessors=%d%n", rt.availableProcessors());
    }

    private static double pct(long[] sortedUs, int p) {
        int i = (int) Math.min(sortedUs.length - 1, Math.ceil(p / 100.0 * sortedUs.length) - 1);
        return sortedUs[Math.max(i, 0)] / 1000.0;
    }
}
