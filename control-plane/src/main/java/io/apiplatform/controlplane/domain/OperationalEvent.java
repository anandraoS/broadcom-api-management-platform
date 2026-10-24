package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "operational_event")
public class OperationalEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant ts;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String severity;
    @Column(name = "route_slug") private String routeSlug;
    @Column(nullable = false) private String message;
    @Column(name = "correlation_id") private String correlationId;

    protected OperationalEvent() {}
    public OperationalEvent(Instant ts, String type, String severity, String routeSlug, String message, String correlationId) {
        this.ts = ts; this.type = type; this.severity = severity; this.routeSlug = routeSlug;
        this.message = message; this.correlationId = correlationId;
    }
    public Long getId() { return id; }
    public Instant getTs() { return ts; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getRouteSlug() { return routeSlug; }
    public String getMessage() { return message; }
    public String getCorrelationId() { return correlationId; }
}
