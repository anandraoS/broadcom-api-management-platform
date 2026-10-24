package io.apiplatform.controlplane.service;

import io.apiplatform.controlplane.domain.*;
import io.apiplatform.controlplane.repo.*;
import io.apiplatform.controlplane.security.CurrentUser;
import io.apiplatform.controlplane.web.ApiException;
import io.apiplatform.controlplane.web.Dtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API keys are 256-bit random secrets, so an unsalted SHA-256 is sufficient for storage (brute force is
 * infeasible; slow password hashes would only add latency to every gateway lookup). The plaintext is shown once.
 * Format: {prefix}.{secret}; the prefix is a non-secret lookup handle.
 */
@Service
public class ConsumerService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConsumerRepository consumers;
    private final SubscriptionRepository subscriptions;
    private final ApiKeyRepository keys;
    private final ApiDefinitionRepository apis;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public ConsumerService(ConsumerRepository consumers, SubscriptionRepository subscriptions, ApiKeyRepository keys,
                           ApiDefinitionRepository apis, AuditService audit, ApplicationEventPublisher events) {
        this.consumers = consumers; this.subscriptions = subscriptions; this.keys = keys; this.apis = apis;
        this.audit = audit; this.events = events;
    }

    @Transactional(readOnly = true)
    public PageView<ConsumerView> search(String q, Pageable pageable) {
        Page<Consumer> p = consumers.search(q == null || q.isBlank() ? null : q.trim(), pageable);
        return PageView.of(p.map(ConsumerService::view));
    }

    @Transactional
    public ConsumerView create(ConsumerRequest r) {
        Consumer c = consumers.save(new Consumer(r.name(), r.clientId(), r.ownerEmail()));
        audit.record("CONSUMER_CREATED", "CONSUMER", c.getId(), "clientId=" + c.getClientId());
        return view(c);
    }

    @Transactional
    public ConsumerView setActive(Long id, boolean active) {
        Consumer c = consumer(id);
        c.setActive(active);
        audit.record(active ? "CONSUMER_ENABLED" : "CONSUMER_DISABLED", "CONSUMER", id, null);
        events.publishEvent(new ConfigChanged("consumer " + id));
        return view(c);
    }

    @Transactional
    public SubscriptionView subscribe(Long consumerId, Long apiId) {
        Consumer c = consumer(consumerId);
        ApiDefinition a = apis.findById(apiId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API not found"));
        if (subscriptions.existsByConsumerIdAndApiId(consumerId, apiId)) {
            throw new ApiException(HttpStatus.CONFLICT, "already_subscribed", "Consumer is already subscribed to this API");
        }
        Subscription s = subscriptions.save(new Subscription(c, a));
        audit.record("SUBSCRIPTION_CREATED", "CONSUMER", consumerId, "api=" + a.getSlug());
        events.publishEvent(new ConfigChanged("subscription " + s.getId()));
        return view(s);
    }

    @Transactional
    public SubscriptionView cancel(Long consumerId, Long subscriptionId) {
        Subscription s = subscriptions.findById(subscriptionId)
                .filter(x -> x.getConsumer().getId().equals(consumerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Subscription not found"));
        s.setStatus("CANCELLED");
        audit.record("SUBSCRIPTION_CANCELLED", "CONSUMER", consumerId, "api=" + s.getApi().getSlug());
        events.publishEvent(new ConfigChanged("subscription " + subscriptionId));
        return view(s);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> subscriptions(Long consumerId) {
        consumer(consumerId);
        return subscriptions.findByConsumerId(consumerId).stream().map(ConsumerService::view).toList();
    }

    @Transactional
    public KeyIssued issueKey(Long consumerId, int validDays) {
        Consumer c = consumer(consumerId);
        String prefix = "k" + randomToken(6);
        String secret = randomToken(32);
        String full = prefix + "." + secret;
        Instant exp = Instant.now().plus(Duration.ofDays(validDays));
        ApiKey k = keys.save(new ApiKey(c, prefix, sha256Hex(full), exp));
        audit.record("API_KEY_ISSUED", "CONSUMER", consumerId, "prefix=" + prefix + " expires=" + exp);
        events.publishEvent(new ConfigChanged("key issued " + k.getId()));
        return new KeyIssued(k.getId(), prefix, full, exp);
    }

    /** Rotation = issue a new key, then revoke the old one; callers can overlap both during migration. */
    @Transactional
    public void revokeKey(Long consumerId, Long keyId) {
        ApiKey k = keys.findById(keyId).filter(x -> x.getConsumer().getId().equals(consumerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Key not found"));
        k.revoke();
        audit.record("API_KEY_REVOKED", "CONSUMER", consumerId, "prefix=" + k.getKeyPrefix() + " by=" + CurrentUser.name());
        events.publishEvent(new ConfigChanged("key revoked " + keyId));
    }

    @Transactional(readOnly = true)
    public List<KeyView> listKeys(Long consumerId) {
        consumer(consumerId);
        return keys.findByConsumerId(consumerId).stream()
                .map(k -> new KeyView(k.getId(), k.getKeyPrefix(), k.getExpiresAt(), k.isRevoked(), k.getCreatedAt())).toList();
    }

    private Consumer consumer(Long id) {
        return consumers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Consumer not found"));
    }

    static ConsumerView view(Consumer c) {
        return new ConsumerView(c.getId(), c.getName(), c.getClientId(), c.getOwnerEmail(), c.isActive(), c.getCreatedAt());
    }

    static SubscriptionView view(Subscription s) {
        return new SubscriptionView(s.getId(), s.getConsumer().getId(), s.getApi().getId(), s.getApi().getSlug(), s.getStatus());
    }

    public static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
