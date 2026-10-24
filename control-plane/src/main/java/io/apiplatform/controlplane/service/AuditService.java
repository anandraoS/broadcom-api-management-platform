package io.apiplatform.controlplane.service;

import io.apiplatform.controlplane.domain.AuditEvent;
import io.apiplatform.controlplane.repo.AuditEventRepository;
import io.apiplatform.controlplane.security.CorrelationFilter;
import io.apiplatform.controlplane.security.CurrentUser;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes inside the caller's transaction: a change and its audit record commit or roll back together. */
@Service
public class AuditService {
    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, String detail) {
        String safe = detail == null ? null : (detail.length() > 1900 ? detail.substring(0, 1900) : detail);
        repo.save(new AuditEvent(CurrentUser.name(), action, entityType, String.valueOf(entityId), safe,
                MDC.get(CorrelationFilter.MDC_KEY)));
    }
}
