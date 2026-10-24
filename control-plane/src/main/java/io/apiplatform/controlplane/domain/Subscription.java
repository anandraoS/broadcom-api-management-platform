package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "subscription")
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "consumer_id") private Consumer consumer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_id") private ApiDefinition api;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected Subscription() {}
    public Subscription(Consumer c, ApiDefinition a) { this.consumer = c; this.api = a; }
    public Long getId() { return id; }
    public Consumer getConsumer() { return consumer; }
    public ApiDefinition getApi() { return api; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public Instant getCreatedAt() { return createdAt; }
}
