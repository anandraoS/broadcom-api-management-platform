package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.domain.Lifecycle;
import io.apiplatform.controlplane.service.ApiService;
import io.apiplatform.controlplane.service.IdempotencyService;
import io.apiplatform.controlplane.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/apis")
public class ApiController {
    private static final java.util.Set<String> SORTABLE = java.util.Set.of("name", "slug", "lifecycle", "updatedAt", "createdAt");
    private final ApiService service;
    private final IdempotencyService idempotency;

    public ApiController(ApiService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    public PageView<ApiView> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Lifecycle lifecycle,
                                    @RequestParam(required = false) String owner,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "name") String sort,
                                    @RequestParam(defaultValue = "asc") String dir) {
        // whitelist sort fields and cap page size: user input never reaches ORDER BY unvalidated
        String field = SORTABLE.contains(sort) ? sort : "name";
        Sort s = Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, field);
        return service.search(q, lifecycle, owner, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), s));
    }

    @GetMapping("/{id}")
    public ApiView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateApiRequest r,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(key, r, ApiView.class, () -> service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ApiView update(@PathVariable Long id, @Valid @RequestBody UpdateApiRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/{id}/lifecycle")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ApiView lifecycle(@PathVariable Long id, @Valid @RequestBody LifecycleRequest r) {
        return service.transition(id, r.target());
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<ApiView> addVersion(@PathVariable Long id, @Valid @RequestBody CreateVersionRequest r) {
        return ResponseEntity.status(201).body(service.addVersion(id, r));
    }

    @PostMapping("/{id}/versions/{versionId}/targets")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<ApiView> addTarget(@PathVariable Long id, @PathVariable Long versionId,
                                             @Valid @RequestBody TargetRequest r) {
        return ResponseEntity.status(201).body(service.addTarget(id, versionId, r));
    }

    @DeleteMapping("/{id}/versions/{versionId}/targets/{targetId}")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ApiView removeTarget(@PathVariable Long id, @PathVariable Long versionId, @PathVariable Long targetId) {
        return service.removeTarget(id, versionId, targetId);
    }
}
