package io.apiplatform.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiplatform.controlplane.domain.*;
import io.apiplatform.controlplane.policy.PolicyConfig;
import io.apiplatform.controlplane.repo.*;
import io.apiplatform.controlplane.service.ConsumerService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SYNTHETIC demo data (app.seed.enabled=true). Idempotent: does nothing if the "orders" API already exists.
 * The demo API key comes from the DEMO_API_KEY environment variable (format "prefix.secret"); if absent, no key is
 * seeded and one can be issued through the API/UI. Nothing here is real customer data.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private final ApiDefinitionRepository apis;
    private final PolicyVersionRepository policies;
    private final ConsumerRepository consumers;
    private final SubscriptionRepository subscriptions;
    private final ApiKeyRepository keys;
    private final TrafficBucketRepository buckets;
    private final OperationalEventRepository events;
    private final ObjectMapper mapper;
    private final String demoKey;
    private final List<String> ordersTargets;
    private final boolean seedTraffic;

    public DemoDataSeeder(ApiDefinitionRepository apis, PolicyVersionRepository policies, ConsumerRepository consumers,
                          SubscriptionRepository subscriptions, ApiKeyRepository keys, TrafficBucketRepository buckets,
                          OperationalEventRepository events, ObjectMapper mapper,
                          @Value("${app.seed.demo-api-key:}") String demoKey,
                          @Value("${app.seed.orders-targets:http://localhost:9001,http://localhost:9002}") List<String> ordersTargets,
                          @Value("${app.seed.traffic:true}") boolean seedTraffic) {
        this.apis = apis; this.policies = policies; this.consumers = consumers; this.subscriptions = subscriptions;
        this.keys = keys; this.buckets = buckets; this.events = events; this.mapper = mapper; this.demoKey = demoKey;
        this.ordersTargets = ordersTargets; this.seedTraffic = seedTraffic;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (apis.findBySlug("orders").isPresent()) return;

        ApiDefinition orders = new ApiDefinition("orders", "Orders API", "orders", "Synthetic order management API");
        ApiVersion v1 = new ApiVersion(orders, "v1", "/orders/v1", "openapi: 3.0.3\ninfo: {title: Orders, version: v1}\npaths: {}");
        int i = 0;
        for (String t : ordersTargets) {
            v1.getTargets().add(new BackendTarget(v1, t.trim(), i++ == 0 ? 2 : 1, "/actuator/health"));
        }
        orders.getVersions().add(v1);
        orders.setLifecycle(Lifecycle.ACTIVE);
        apis.save(orders);

        ApiDefinition payments = new ApiDefinition("payments", "Payments API", "payments", "Synthetic payments API (draft)");
        apis.save(payments);

        PolicyConfig pc = new PolicyConfig(PolicyConfig.AuthType.API_KEY, null, Set.of("GET", "POST"), 120, 20,
                PolicyConfig.RateAlgorithm.TOKEN_BUCKET, 3000, 65_536L, null, true);
        PolicyVersion pv = new PolicyVersion(orders, 1, mapper.writeValueAsString(pc), "seed", "initial synthetic policy");
        pv.decide(PolicyStatus.ACTIVE, "seed-approver");
        policies.save(pv);

        Consumer partner = consumers.save(new Consumer("Acme Partner App", "acme-partner", "dev-contact@example.com"));
        consumers.save(new Consumer("Internal Reporting", "internal-reporting", "reporting@example.com"));
        subscriptions.save(new Subscription(partner, orders));
        if (demoKey != null && demoKey.matches("[A-Za-z0-9_-]{4,16}\\.[A-Za-z0-9_-]{24,}")) {
            String prefix = demoKey.substring(0, demoKey.indexOf('.'));
            keys.save(new ApiKey(partner, prefix, ConsumerService.sha256Hex(demoKey), Instant.now().plus(Duration.ofDays(90))));
            log.info("seeded demo API key with prefix {}", prefix);
        }
        if (seedTraffic) seedTraffic();
        log.info("demo data seeded");
    }

    /** 30 one-minute buckets: healthy "orders" plus a degraded "legacy-billing" route so the incident view has content. */
    private void seedTraffic() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String batch = "seed-" + UUID.randomUUID();
        for (int m = 30; m >= 1; m--) {
            Instant t = now.minus(m, ChronoUnit.MINUTES);
            buckets.save(new TrafficBucket(batch, "orders", t, 600, 12, 3, 2, 4, 0, 600L * 45, 180));
            boolean degraded = m <= 10;
            buckets.save(new TrafficBucket(batch, "legacy-billing", t, 200, 6, degraded ? 40 : 2, 0, 1,
                    degraded ? 12 : 0, degraded ? 200L * 2400 : 200L * 120, degraded ? 9000 : 400));
        }
        events.save(new OperationalEvent(now.minus(9, ChronoUnit.MINUTES), "CIRCUIT_OPEN", "ERROR", "legacy-billing",
                "synthetic: target http://billing-1:8080 opened circuit after 5 consecutive failures", null));
        events.save(new OperationalEvent(now.minus(12, ChronoUnit.MINUTES), "TARGET_UNHEALTHY", "WARN", "legacy-billing",
                "synthetic: health check failed for http://billing-1:8080", null));
    }
}
