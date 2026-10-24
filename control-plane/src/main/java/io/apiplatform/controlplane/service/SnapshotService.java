package io.apiplatform.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiplatform.controlplane.domain.*;
import io.apiplatform.controlplane.policy.DestinationValidator;
import io.apiplatform.controlplane.policy.PolicyConfig;
import io.apiplatform.controlplane.repo.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Builds the gateway snapshot. Consistency rules:
 *  - the in-memory copy is invalidated AFTER the changing transaction commits (never exposes uncommitted state);
 *  - a 30s scheduled invalidation covers missed events in multi-instance control planes;
 *  - routes without an ACTIVE policy are not published (fail closed);
 *  - targets are re-validated against the destination allowlist (defence in depth against bad stored data).
 */
@Service
public class SnapshotService {
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private final ApiDefinitionRepository apis;
    private final PolicyVersionRepository policies;
    private final ApiKeyRepository keys;
    private final SubscriptionRepository subscriptions;
    private final DestinationValidator destinations;
    private final ObjectMapper mapper;
    private final AtomicReference<GatewaySnapshot> cache = new AtomicReference<>();

    public SnapshotService(ApiDefinitionRepository apis, PolicyVersionRepository policies, ApiKeyRepository keys,
                           SubscriptionRepository subscriptions, DestinationValidator destinations, ObjectMapper mapper) {
        this.apis = apis; this.policies = policies; this.keys = keys; this.subscriptions = subscriptions;
        this.destinations = destinations; this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChange(ConfigChanged e) {
        cache.set(null);
    }

    @Scheduled(fixedDelay = 30_000)
    void periodicInvalidate() {
        cache.set(null);
    }

    /** Transactional here (not only on build()) because build() is a self-invocation and would bypass the proxy. */
    @Transactional(readOnly = true)
    public GatewaySnapshot current() {
        GatewaySnapshot s = cache.get();
        if (s == null) {
            s = build();
            cache.set(s);
        }
        return s;
    }

    @Transactional(readOnly = true)
    public GatewaySnapshot build() {
        List<GatewaySnapshot.RouteEntry> routes = new ArrayList<>();
        Set<String> activeSlugs = new HashSet<>();
        for (ApiDefinition a : apis.findAllWithVersionsByLifecycle(Lifecycle.ACTIVE)) {
            Optional<PolicyVersion> pol = policies.findByApiIdAndStatus(a.getId(), PolicyStatus.ACTIVE);
            if (pol.isEmpty()) continue;
            PolicyConfig cfg = parse(pol.get().getConfigJson());
            for (ApiVersion v : a.getVersions()) {
                if (v.getStatus() != Lifecycle.ACTIVE) continue;
                List<GatewaySnapshot.TargetEntry> ts = new ArrayList<>();
                for (BackendTarget t : v.getTargets()) {
                    if (destinations.validate(t.getUrl()).isEmpty()) {
                        ts.add(new GatewaySnapshot.TargetEntry(t.getUrl(), t.getWeight(), t.getHealthPath()));
                    } else {
                        log.warn("excluding unsafe target from snapshot api={} url={}", a.getSlug(), t.getUrl());
                    }
                }
                if (ts.isEmpty()) continue;
                routes.add(new GatewaySnapshot.RouteEntry(a.getSlug(), v.getLabel(), v.getBasePath(), ts, cfg, pol.get().getVersionNo()));
                activeSlugs.add(a.getSlug());
            }
        }
        routes.sort(Comparator.comparing(GatewaySnapshot.RouteEntry::basePath));

        Map<Long, List<String>> subsByConsumer = new HashMap<>();
        for (Subscription s : subscriptions.findByStatus("ACTIVE")) {
            if (activeSlugs.contains(s.getApi().getSlug())) {
                subsByConsumer.computeIfAbsent(s.getConsumer().getId(), k -> new ArrayList<>()).add(s.getApi().getSlug());
            }
        }
        List<GatewaySnapshot.KeyEntry> keyEntries = new ArrayList<>();
        for (ApiKey k : keys.findAll()) {
            if (k.isRevoked() || !k.getConsumer().isActive() || k.getExpiresAt().isBefore(Instant.now())) continue;
            List<String> slugs = subsByConsumer.getOrDefault(k.getConsumer().getId(), List.of());
            if (slugs.isEmpty()) continue;
            keyEntries.add(new GatewaySnapshot.KeyEntry(k.getKeyPrefix(), k.getKeyHash(), k.getExpiresAt(),
                    k.getConsumer().getClientId(), slugs.stream().sorted().toList()));
        }
        keyEntries.sort(Comparator.comparing(GatewaySnapshot.KeyEntry::prefix));
        return new GatewaySnapshot(hash(routes, keyEntries), Instant.now(), routes, keyEntries);
    }

    private String hash(List<?> routes, List<?> keyEntries) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(List.of(routes, keyEntries));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 32);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private PolicyConfig parse(String json) {
        try { return mapper.readValue(json, PolicyConfig.class); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
