package io.apiplatform.controlplane.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "api_definition")
public class ApiDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String name;
    @Column(name = "owner_team", nullable = false) private String ownerTeam;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Lifecycle lifecycle = Lifecycle.DRAFT;
    private String description;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    /** Optimistic locking: concurrent edits of one API fail with 409 instead of silently overwriting. */
    @Version private long version;
    @OneToMany(mappedBy = "api", cascade = CascadeType.ALL, orphanRemoval = true) private List<ApiVersion> versions = new ArrayList<>();

    protected ApiDefinition() {}
    public ApiDefinition(String slug, String name, String ownerTeam, String description) {
        this.slug = slug; this.name = name; this.ownerTeam = ownerTeam; this.description = description;
    }
    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; touch(); }
    public String getOwnerTeam() { return ownerTeam; }
    public Lifecycle getLifecycle() { return lifecycle; }
    public void setLifecycle(Lifecycle l) { this.lifecycle = l; touch(); }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<ApiVersion> getVersions() { return versions; }
    private void touch() { this.updatedAt = Instant.now(); }
}
