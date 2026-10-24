package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "backend_target")
public class BackendTarget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "api_version_id") private ApiVersion apiVersion;
    @Column(nullable = false) private String url;
    private int weight = 1;
    @Column(name = "health_path", nullable = false) private String healthPath = "/actuator/health";

    protected BackendTarget() {}
    public BackendTarget(ApiVersion v, String url, int weight, String healthPath) {
        this.apiVersion = v; this.url = url; this.weight = weight; this.healthPath = healthPath;
    }
    public Long getId() { return id; }
    public ApiVersion getApiVersion() { return apiVersion; }
    public String getUrl() { return url; }
    public int getWeight() { return weight; }
    public String getHealthPath() { return healthPath; }
}
