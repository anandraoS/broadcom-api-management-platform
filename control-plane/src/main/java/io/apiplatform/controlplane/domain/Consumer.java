package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consumer")
public class Consumer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String name;
    @Column(name = "client_id", nullable = false, unique = true) private String clientId;
    @Column(name = "owner_email", nullable = false) private String ownerEmail;
    private boolean active = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected Consumer() {}
    public Consumer(String name, String clientId, String ownerEmail) {
        this.name = name; this.clientId = clientId; this.ownerEmail = ownerEmail;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getClientId() { return clientId; }
    public String getOwnerEmail() { return ownerEmail; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public Instant getCreatedAt() { return createdAt; }
}
