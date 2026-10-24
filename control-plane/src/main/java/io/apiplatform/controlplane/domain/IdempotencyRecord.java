package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    @Id @Column(name = "idem_key") private String key;
    @Column(name = "request_hash", nullable = false) private String requestHash;
    @Column(name = "response_status", nullable = false) private int responseStatus;
    @Column(name = "response_body", columnDefinition = "LONGTEXT") private String responseBody;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {}
    public IdempotencyRecord(String key, String requestHash, int status, String body) {
        this.key = key; this.requestHash = requestHash; this.responseStatus = status; this.responseBody = body;
    }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
}
