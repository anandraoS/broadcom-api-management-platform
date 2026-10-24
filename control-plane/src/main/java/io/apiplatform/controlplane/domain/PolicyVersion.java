package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "policy_version")
public class PolicyVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_id") private ApiDefinition api;
    @Column(name = "version_no", nullable = false) private int versionNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PolicyStatus status;
    @Column(name = "config_json", nullable = false, columnDefinition = "LONGTEXT") private String configJson;
    @Column(name = "submitted_by", nullable = false) private String submittedBy;
    @Column(name = "approved_by") private String approvedBy;
    @Column(name = "comment_text") private String comment;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "decided_at") private Instant decidedAt;

    protected PolicyVersion() {}
    public PolicyVersion(ApiDefinition api, int versionNo, String configJson, String submittedBy, String comment) {
        this.api = api; this.versionNo = versionNo; this.configJson = configJson;
        this.submittedBy = submittedBy; this.comment = comment; this.status = PolicyStatus.PENDING_APPROVAL;
    }
    public Long getId() { return id; }
    public ApiDefinition getApi() { return api; }
    public int getVersionNo() { return versionNo; }
    public PolicyStatus getStatus() { return status; }
    public void setStatus(PolicyStatus s) { this.status = s; }
    public String getConfigJson() { return configJson; }
    public String getSubmittedBy() { return submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void decide(PolicyStatus s, String by) { this.status = s; this.approvedBy = by; this.decidedAt = Instant.now(); }
}
