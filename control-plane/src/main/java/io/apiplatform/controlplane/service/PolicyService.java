package io.apiplatform.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiplatform.controlplane.domain.*;
import io.apiplatform.controlplane.policy.PolicyConfig;
import io.apiplatform.controlplane.policy.PolicyValidator;
import io.apiplatform.controlplane.repo.ApiDefinitionRepository;
import io.apiplatform.controlplane.repo.PolicyVersionRepository;
import io.apiplatform.controlplane.security.AccessGuard;
import io.apiplatform.controlplane.security.CurrentUser;
import io.apiplatform.controlplane.web.ApiException;
import io.apiplatform.controlplane.web.Dtos.*;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy change lifecycle: submit -> validate -> (four-eyes) approve -> version -> apply -> audit -> revert.
 * "Apply" means the version becomes ACTIVE in the control plane and is included in the next gateway snapshot;
 * gateways pick it up on their next poll (eventual consistency, see docs/policy-propagation.md).
 */
@Service
public class PolicyService {
    private final PolicyVersionRepository policies;
    private final ApiDefinitionRepository apis;
    private final PolicyValidator validator;
    private final AccessGuard guard;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public PolicyService(PolicyVersionRepository policies, ApiDefinitionRepository apis, PolicyValidator validator,
                         AccessGuard guard, AuditService audit, ObjectMapper mapper, ApplicationEventPublisher events) {
        this.policies = policies; this.apis = apis; this.validator = validator; this.guard = guard;
        this.audit = audit; this.mapper = mapper; this.events = events;
    }

    public ValidationResult validate(PolicyConfig config) {
        List<String> errors = validator.validate(config);
        return new ValidationResult(errors.isEmpty(), errors);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PolicyView submit(Long apiId, PolicyConfig config, String comment) {
        ApiDefinition api = api(apiId);
        guard.assertCanModify(api);
        List<String> errors = validator.validate(config);
        if (!errors.isEmpty()) {
            audit.record("POLICY_REJECTED_INVALID", "API", apiId, String.join("; ", errors));
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_policy", "Policy validation failed", errors);
        }
        PolicyVersion pv = new PolicyVersion(api, policies.maxVersionNo(apiId) + 1, toJson(config), CurrentUser.name(), comment);
        policies.save(pv);
        audit.record("POLICY_SUBMITTED", "API", apiId, "version=" + pv.getVersionNo());
        return toView(pv);
    }

    @Transactional
    public PolicyView approve(Long policyId, String comment) {
        PolicyVersion pv = find(policyId);
        requireApprover();
        if (pv.getStatus() != PolicyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "not_pending", "Policy is not pending approval");
        }
        if (pv.getSubmittedBy().equals(CurrentUser.name())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "self_approval", "A policy cannot be approved by its submitter");
        }
        // Re-validate at approval time: rules may have tightened since submission.
        List<String> errors = validator.validate(fromJson(pv.getConfigJson()));
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_policy", "Policy no longer valid", errors);
        }
        activate(pv, CurrentUser.name());
        audit.record("POLICY_APPROVED", "API", pv.getApi().getId(), "version=" + pv.getVersionNo()
                + (comment == null ? "" : " comment=" + comment));
        return toView(pv);
    }

    @Transactional
    public PolicyView reject(Long policyId, String comment) {
        PolicyVersion pv = find(policyId);
        requireApprover();
        if (pv.getStatus() != PolicyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "not_pending", "Policy is not pending approval");
        }
        pv.decide(PolicyStatus.REJECTED, CurrentUser.name());
        audit.record("POLICY_REJECTED", "API", pv.getApi().getId(), "version=" + pv.getVersionNo()
                + (comment == null ? "" : " comment=" + comment));
        return toView(pv);
    }

    /**
     * Emergency revert: re-activates an earlier configuration as a NEW version (history is never rewritten).
     * Restricted to approvers/admins; deliberately bypasses four-eyes to allow fast incident response, but is audited.
     */
    @Transactional
    public PolicyView revert(Long apiId, int toVersionNo) {
        ApiDefinition api = api(apiId);
        requireApprover();
        PolicyVersion old = policies.findByApiIdAndVersionNo(apiId, toVersionNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Policy version not found"));
        if (old.getStatus() == PolicyStatus.REJECTED || old.getStatus() == PolicyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "not_revertable", "Only previously active versions can be reverted to");
        }
        List<String> errors = validator.validate(fromJson(old.getConfigJson()));
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_policy", "Target version no longer valid", errors);
        }
        PolicyVersion pv = new PolicyVersion(api, policies.maxVersionNo(apiId) + 1, old.getConfigJson(),
                CurrentUser.name(), "revert to v" + toVersionNo);
        policies.save(pv);
        activate(pv, CurrentUser.name());
        audit.record("POLICY_REVERTED", "API", apiId, "to=v" + toVersionNo + " new=v" + pv.getVersionNo());
        return toView(pv);
    }

    @Transactional(readOnly = true)
    public List<PolicyView> history(Long apiId) {
        api(apiId);
        return policies.findByApiIdOrderByVersionNoDesc(apiId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<PolicyView> pending() {
        return policies.findByStatus(PolicyStatus.PENDING_APPROVAL).stream().map(this::toView).toList();
    }

    private void activate(PolicyVersion pv, String by) {
        policies.findByApiIdAndStatus(pv.getApi().getId(), PolicyStatus.ACTIVE).ifPresent(prev -> {
            if (!prev.getId().equals(pv.getId())) prev.setStatus(PolicyStatus.SUPERSEDED);
        });
        policies.flush();
        pv.decide(PolicyStatus.ACTIVE, by);
        events.publishEvent(new ConfigChanged("policy " + pv.getApi().getId() + " v" + pv.getVersionNo()));
    }

    private void requireApprover() {
        if (!(CurrentUser.hasRole("POLICY_APPROVER") || CurrentUser.hasRole("ADMIN"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "POLICY_APPROVER role required");
        }
    }

    private ApiDefinition api(Long id) {
        return apis.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API not found"));
    }

    private PolicyVersion find(Long id) {
        return policies.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Policy not found"));
    }

    PolicyView toView(PolicyVersion p) {
        return new PolicyView(p.getId(), p.getApi().getId(), p.getVersionNo(), p.getStatus(), fromJson(p.getConfigJson()),
                p.getSubmittedBy(), p.getApprovedBy(), p.getComment(), p.getCreatedAt(), p.getDecidedAt());
    }

    private String toJson(PolicyConfig c) {
        try { return mapper.writeValueAsString(c); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    PolicyConfig fromJson(String json) {
        try { return mapper.readValue(json, PolicyConfig.class); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
