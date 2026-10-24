package io.apiplatform.controlplane.incident;

import io.apiplatform.controlplane.domain.OperationalEvent;
import io.apiplatform.controlplane.repo.AuditEventRepository;
import io.apiplatform.controlplane.repo.OperationalEventRepository;
import io.apiplatform.controlplane.service.TelemetryService;
import io.apiplatform.controlplane.web.Dtos.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
    private final TelemetryService telemetry;
    private final OperationalEventRepository events;
    private final AuditEventRepository audit;

    public IncidentService(TelemetryService telemetry, OperationalEventRepository events, AuditEventRepository audit) {
        this.telemetry = telemetry;
        this.events = events;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public IncidentReport report(int windowMinutes) {
        Instant since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);
        TrafficSummary summary = telemetry.summary(windowMinutes);
        List<Finding> findings = new ArrayList<>(IncidentRules.evaluate(summary.routes()));

        List<OperationalEvent> evs = events.findByTsAfterOrderByTsDesc(since);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OperationalEvent e : evs) counts.merge(e.getType() + "|" + (e.getRouteSlug() == null ? "" : e.getRouteSlug()), 1L, Long::sum);
        counts.forEach((k, v) -> {
            String[] p = k.split("\\|", -1);
            Finding f = IncidentRules.fromEventType(p[0], p.length > 1 && !p[1].isEmpty() ? p[1] : null, v);
            if (f != null) findings.add(f);
        });

        List<AuditView> changes = audit.findByTsAfterOrderByTsDesc(since).stream().limit(50)
                .map(a -> new AuditView(a.getId(), a.getTs(), a.getActor(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getDetail(), a.getCorrelationId())).toList();
        List<EventView> recent = evs.stream().limit(50).map(e -> new EventView(e.getId(), e.getTs(), e.getType(),
                e.getSeverity(), e.getRouteSlug(), e.getMessage(), e.getCorrelationId())).toList();
        return new IncidentReport(Instant.now(), windowMinutes, findings, changes, recent,
                "Findings are produced by deterministic threshold rules over aggregated gateway telemetry; "
                        + "each lists its evidence. No AI-generated hypotheses are included.");
    }
}
