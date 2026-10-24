package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only: no setters and no update/delete path in the service layer. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant ts = Instant.now();
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private String action;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id", nullable = false) private String entityId;
    private String detail;
    @Column(name = "correlation_id") private String correlationId;

    protected AuditEvent() {}
    public AuditEvent(String actor, String action, String entityType, String entityId, String detail, String correlationId) {
        this.actor = actor; this.action = action; this.entityType = entityType; this.entityId = entityId;
        this.detail = detail; this.correlationId = correlationId;
    }
    public Long getId() { return id; }
    public Instant getTs() { return ts; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetail() { return detail; }
    public String getCorrelationId() { return correlationId; }
}
