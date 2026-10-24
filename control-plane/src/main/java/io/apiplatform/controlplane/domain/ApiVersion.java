package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "api_version")
public class ApiVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_id") private ApiDefinition api;
    @Column(nullable = false) private String label;
    @Column(name = "base_path", nullable = false, unique = true) private String basePath;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Lifecycle status = Lifecycle.ACTIVE;
    @Column(name = "openapi_spec", columnDefinition = "LONGTEXT") private String openapiSpec;
    @OneToMany(mappedBy = "apiVersion", cascade = CascadeType.ALL, orphanRemoval = true) private List<BackendTarget> targets = new ArrayList<>();

    protected ApiVersion() {}
    public ApiVersion(ApiDefinition api, String label, String basePath, String openapiSpec) {
        this.api = api; this.label = label; this.basePath = basePath; this.openapiSpec = openapiSpec;
    }
    public Long getId() { return id; }
    public ApiDefinition getApi() { return api; }
    public String getLabel() { return label; }
    public String getBasePath() { return basePath; }
    public Lifecycle getStatus() { return status; }
    public void setStatus(Lifecycle s) { this.status = s; }
    public String getOpenapiSpec() { return openapiSpec; }
    public List<BackendTarget> getTargets() { return targets; }
}
