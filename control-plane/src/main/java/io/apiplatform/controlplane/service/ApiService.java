package io.apiplatform.controlplane.service;

import io.apiplatform.controlplane.domain.*;
import io.apiplatform.controlplane.policy.DestinationValidator;
import io.apiplatform.controlplane.repo.*;
import io.apiplatform.controlplane.security.AccessGuard;
import io.apiplatform.controlplane.web.ApiException;
import io.apiplatform.controlplane.web.Dtos.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiService {
    private final ApiDefinitionRepository apis;
    private final ApiVersionRepository versions;
    private final BackendTargetRepository targets;
    private final AccessGuard guard;
    private final AuditService audit;
    private final DestinationValidator destinations;
    private final ApplicationEventPublisher events;

    public ApiService(ApiDefinitionRepository apis, ApiVersionRepository versions, BackendTargetRepository targets,
                      AccessGuard guard, AuditService audit, DestinationValidator destinations,
                      ApplicationEventPublisher events) {
        this.apis = apis; this.versions = versions; this.targets = targets; this.guard = guard;
        this.audit = audit; this.destinations = destinations; this.events = events;
    }

    @Transactional(readOnly = true)
    public PageView<ApiView> search(String q, Lifecycle lifecycle, String owner, Pageable pageable) {
        Page<ApiDefinition> page = apis.search(blankToNull(q), lifecycle, blankToNull(owner), pageable);
        return PageView.of(page.map(a -> toView(a, false)));
    }

    @Transactional(readOnly = true)
    public ApiView get(Long id) {
        return toView(find(id), true);
    }

    @Transactional
    public ApiView create(CreateApiRequest r) {
        if (apis.findBySlug(r.slug()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "slug_taken", "An API with this slug already exists");
        }
        // API_OWNER may only create APIs for their own team
        ApiDefinition probe = new ApiDefinition(r.slug(), r.name(), r.ownerTeam(), r.description());
        guard.assertCanModify(probe);
        ApiDefinition saved = apis.save(probe);
        audit.record("API_CREATED", "API", saved.getId(), "slug=" + saved.getSlug() + " team=" + saved.getOwnerTeam());
        return toView(saved, true);
    }

    @Transactional
    public ApiView update(Long id, UpdateApiRequest r) {
        ApiDefinition a = find(id);
        guard.assertCanModify(a);
        if (a.getVersion() != r.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "concurrent_modification", "Stale version; reload and retry");
        }
        a.setName(r.name());
        a.setDescription(r.description());
        apis.saveAndFlush(a);
        audit.record("API_UPDATED", "API", id, "name=" + r.name());
        return toView(a, true);
    }

    @Transactional
    public ApiView transition(Long id, Lifecycle target) {
        ApiDefinition a = find(id);
        guard.assertCanModify(a);
        if (!a.getLifecycle().canMoveTo(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid_transition",
                    "Cannot move from " + a.getLifecycle() + " to " + target);
        }
        Lifecycle from = a.getLifecycle();
        a.setLifecycle(target);
        audit.record("API_LIFECYCLE", "API", id, from + " -> " + target);
        events.publishEvent(new ConfigChanged("api lifecycle " + id));
        return toView(a, true);
    }

    @Transactional
    public ApiView addVersion(Long apiId, CreateVersionRequest r) {
        ApiDefinition a = find(apiId);
        guard.assertCanModify(a);
        if (versions.existsByBasePath(r.basePath())) {
            throw new ApiException(HttpStatus.CONFLICT, "path_taken", "basePath is already used by another API version");
        }
        if (a.getVersions().stream().anyMatch(v -> v.getLabel().equals(r.label()))) {
            throw new ApiException(HttpStatus.CONFLICT, "version_exists", "Version label already exists");
        }
        ApiVersion v = new ApiVersion(a, r.label(), r.basePath(), r.openapiSpec());
        a.getVersions().add(v);
        apis.saveAndFlush(a);
        audit.record("API_VERSION_ADDED", "API", apiId, r.label() + " " + r.basePath());
        events.publishEvent(new ConfigChanged("version added " + apiId));
        return toView(a, true);
    }

    /** noRollbackFor: the rejection audit record must survive the 422 (nothing else is written before it). */
    @Transactional(noRollbackFor = ApiException.class)
    public ApiView addTarget(Long apiId, Long versionId, TargetRequest r) {
        ApiDefinition a = find(apiId);
        guard.assertCanModify(a);
        ApiVersion v = versions.findById(versionId).filter(x -> x.getApi().getId().equals(apiId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API version not found"));
        var errors = destinations.validate(r.url());
        if (!errors.isEmpty()) {
            audit.record("TARGET_REJECTED", "API", apiId, "url=" + safeUrl(r.url()) + " reasons=" + errors);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "unsafe_destination", "Backend destination rejected", errors);
        }
        String health = r.healthPath() == null ? "/actuator/health" : r.healthPath();
        v.getTargets().add(new BackendTarget(v, r.url(), r.weight(), health));
        versions.saveAndFlush(v);
        audit.record("TARGET_ADDED", "API", apiId, "version=" + v.getLabel() + " url=" + safeUrl(r.url()));
        events.publishEvent(new ConfigChanged("target added " + apiId));
        return toView(a, true);
    }

    @Transactional
    public ApiView removeTarget(Long apiId, Long versionId, Long targetId) {
        ApiDefinition a = find(apiId);
        guard.assertCanModify(a);
        ApiVersion v = versions.findById(versionId).filter(x -> x.getApi().getId().equals(apiId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API version not found"));
        if (!v.getTargets().removeIf(t -> t.getId().equals(targetId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Target not found");
        }
        versions.saveAndFlush(v);
        audit.record("TARGET_REMOVED", "API", apiId, "version=" + v.getLabel() + " target=" + targetId);
        events.publishEvent(new ConfigChanged("target removed " + apiId));
        return toView(a, true);
    }

    ApiDefinition find(Long id) {
        return apis.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API not found"));
    }

    static ApiView toView(ApiDefinition a, boolean withVersions) {
        var vs = withVersions ? a.getVersions().stream().map(ApiService::toView).toList() : java.util.List.<VersionView>of();
        return new ApiView(a.getId(), a.getSlug(), a.getName(), a.getOwnerTeam(), a.getLifecycle(), a.getDescription(),
                a.getVersion(), a.getCreatedAt(), a.getUpdatedAt(), vs);
    }

    static VersionView toView(ApiVersion v) {
        return new VersionView(v.getId(), v.getLabel(), v.getBasePath(), v.getStatus(),
                v.getOpenapiSpec() != null && !v.getOpenapiSpec().isBlank(),
                v.getTargets().stream().map(t -> new TargetView(t.getId(), t.getUrl(), t.getWeight(), t.getHealthPath())).toList());
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Never write potential credentials from a rejected URL into audit logs. */
    private static String safeUrl(String url) {
        return url == null ? "" : url.replaceAll("//[^/@]*@", "//***@");
    }
}
