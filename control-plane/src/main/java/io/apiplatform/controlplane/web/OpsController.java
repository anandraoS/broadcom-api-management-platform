package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.incident.IncidentService;
import io.apiplatform.controlplane.repo.AuditEventRepository;
import io.apiplatform.controlplane.service.TelemetryService;
import io.apiplatform.controlplane.web.Dtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Operations read-models: audit history, traffic summary, events and the incident investigation report. */
@RestController
@RequestMapping("/api/v1")
public class OpsController {
    private final AuditEventRepository audit;
    private final TelemetryService telemetry;
    private final IncidentService incidents;

    public OpsController(AuditEventRepository audit, TelemetryService telemetry, IncidentService incidents) {
        this.audit = audit;
        this.telemetry = telemetry;
        this.incidents = incidents;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','POLICY_APPROVER')")
    @Transactional(readOnly = true)
    public PageView<AuditView> audit(@RequestParam(required = false) String entityType,
                                     @RequestParam(required = false) String entityId,
                                     @RequestParam(required = false) String actor,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size) {
        var p = audit.search(blank(entityType), blank(entityId), blank(actor),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "ts")));
        return PageView.of(p.map(a -> new AuditView(a.getId(), a.getTs(), a.getActor(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetail(), a.getCorrelationId())));
    }

    @GetMapping("/traffic/summary")
    public TrafficSummary traffic(@RequestParam(defaultValue = "15") int windowMinutes) {
        return telemetry.summary(Math.min(Math.max(windowMinutes, 1), 1440));
    }

    @GetMapping("/events")
    public PageView<EventView> events(@RequestParam(required = false) String type,
                                      @RequestParam(required = false) String route,
                                      @RequestParam(required = false) String severity,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        return telemetry.events(type, route, severity,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "ts")));
    }

    @GetMapping("/incidents")
    public IncidentReport incidents(@RequestParam(defaultValue = "15") int windowMinutes) {
        return incidents.report(Math.min(Math.max(windowMinutes, 1), 1440));
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
