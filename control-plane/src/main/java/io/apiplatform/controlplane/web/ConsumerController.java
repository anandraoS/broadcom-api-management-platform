package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.service.ConsumerService;
import io.apiplatform.controlplane.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/consumers")
public class ConsumerController {
    private final ConsumerService service;

    public ConsumerController(ConsumerService service) {
        this.service = service;
    }

    @GetMapping
    public PageView<ConsumerView> search(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.search(q, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<ConsumerView> create(@Valid @RequestBody ConsumerRequest r) {
        return ResponseEntity.status(201).body(service.create(r));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ConsumerView enable(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ConsumerView disable(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @GetMapping("/{id}/subscriptions")
    public List<SubscriptionView> subscriptions(@PathVariable Long id) {
        return service.subscriptions(id);
    }

    @PostMapping("/{id}/subscriptions")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<SubscriptionView> subscribe(@PathVariable Long id, @Valid @RequestBody SubscribeRequest r) {
        return ResponseEntity.status(201).body(service.subscribe(id, r.apiId()));
    }

    @DeleteMapping("/{id}/subscriptions/{subId}")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public SubscriptionView cancel(@PathVariable Long id, @PathVariable Long subId) {
        return service.cancel(id, subId);
    }

    @GetMapping("/{id}/keys")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public List<KeyView> keys(@PathVariable Long id) {
        return service.listKeys(id);
    }

    @PostMapping("/{id}/keys")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<KeyIssued> issue(@PathVariable Long id, @Valid @RequestBody IssueKeyRequest r) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(service.issueKey(id, r.validDays()));
    }

    @DeleteMapping("/{id}/keys/{keyId}")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<Void> revoke(@PathVariable Long id, @PathVariable Long keyId) {
        service.revokeKey(id, keyId);
        return ResponseEntity.noContent().build();
    }
}
