package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.policy.PolicyConfig;
import io.apiplatform.controlplane.service.PolicyService;
import io.apiplatform.controlplane.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PolicyController {
    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    /** Dry-run validation: no state change, safe to call from the UI on every edit. */
    @PostMapping("/policies/validate")
    public ValidationResult validate(@RequestBody PolicyConfig config) {
        return service.validate(config);
    }

    @GetMapping("/apis/{apiId}/policies")
    public List<PolicyView> history(@PathVariable Long apiId) {
        return service.history(apiId);
    }

    @PostMapping("/apis/{apiId}/policies")
    @PreAuthorize("hasAnyRole('ADMIN','API_OWNER')")
    public ResponseEntity<PolicyView> submit(@PathVariable Long apiId, @Valid @RequestBody PolicySubmitRequest r) {
        return ResponseEntity.status(201).body(service.submit(apiId, r.config(), r.comment()));
    }

    @GetMapping("/policies/pending")
    @PreAuthorize("hasAnyRole('ADMIN','POLICY_APPROVER')")
    public List<PolicyView> pending() {
        return service.pending();
    }

    @PostMapping("/policies/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','POLICY_APPROVER')")
    public PolicyView approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest r) {
        return service.approve(id, r == null ? null : r.comment());
    }

    @PostMapping("/policies/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','POLICY_APPROVER')")
    public PolicyView reject(@PathVariable Long id, @RequestBody(required = false) DecisionRequest r) {
        return service.reject(id, r == null ? null : r.comment());
    }

    @PostMapping("/apis/{apiId}/policies/revert")
    @PreAuthorize("hasAnyRole('ADMIN','POLICY_APPROVER')")
    public PolicyView revert(@PathVariable Long apiId, @Valid @RequestBody RevertRequest r) {
        return service.revert(apiId, r.toVersionNo());
    }
}
