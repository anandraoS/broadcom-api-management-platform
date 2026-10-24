package io.apiplatform.sample;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Synthetic backend used to demonstrate gateway routing, timeouts, retries and failure handling. */
@SpringBootApplication
@RestController
public class OrdersApplication {

    public record Order(long id, String sku, int quantity) {}
    public record NewOrder(String sku, int quantity) {}

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private final AtomicLong flakyCalls = new AtomicLong();
    private final String instance;

    public OrdersApplication(@Value("${sample.instance:orders-1}") String instance) {
        this.instance = instance;
        orders.put(1L, new Order(1, "SKU-RED-1", 2));
        orders.put(2L, new Order(2, "SKU-BLUE-9", 1));
    }

    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }

    @GetMapping("/orders")
    public Map<String, Object> list() {
        return Map.of("instance", instance, "items", List.copyOf(orders.values()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> get(@PathVariable long id) {
        Order o = orders.get(id);
        return o == null ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "order not found"))
                : ResponseEntity.ok(o);
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@RequestBody NewOrder n) {
        Order o = new Order(ids.incrementAndGet(), n.sku(), n.quantity());
        orders.put(o.id(), o);
        return ResponseEntity.status(HttpStatus.CREATED).body(o);
    }

    /** Sleeps to simulate a slow dependency (capped to keep the demo safe). */
    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1000") long ms) throws InterruptedException {
        Thread.sleep(Math.min(ms, 30_000));
        return Map.of("instance", instance, "sleptMs", Math.min(ms, 30_000));
    }

    /** Fails with the given probability (0-100). */
    @GetMapping("/flaky")
    public ResponseEntity<Map<String, Object>> flaky(@RequestParam(defaultValue = "50") int failPercent) {
        flakyCalls.incrementAndGet();
        if (ThreadLocalRandom.current().nextInt(100) < failPercent) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "simulated failure"));
        }
        return ResponseEntity.ok(Map.of("instance", instance, "ok", true));
    }

    @GetMapping("/fail")
    public ResponseEntity<Map<String, Object>> fail() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "always failing"));
    }

    @RequestMapping("/echo")
    public Map<String, Object> echo(@RequestHeader Map<String, String> headers, @RequestBody(required = false) String body) {
        return Map.of("instance", instance, "headers", headers, "bodyLength", body == null ? 0 : body.length());
    }
}
