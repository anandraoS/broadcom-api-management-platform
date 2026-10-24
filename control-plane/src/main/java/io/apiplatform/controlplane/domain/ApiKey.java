package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "api_key")
public class ApiKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "consumer_id") private Consumer consumer;
    @Column(name = "key_prefix", nullable = false, unique = true) private String keyPrefix;
    @Column(name = "key_hash", nullable = false) private String keyHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    private boolean revoked;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected ApiKey() {}
    public ApiKey(Consumer c, String prefix, String hash, Instant expiresAt) {
        this.consumer = c; this.keyPrefix = prefix; this.keyHash = hash; this.expiresAt = expiresAt;
    }
    public Long getId() { return id; }
    public Consumer getConsumer() { return consumer; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void revoke() { this.revoked = true; }
    public Instant getCreatedAt() { return createdAt; }
}
