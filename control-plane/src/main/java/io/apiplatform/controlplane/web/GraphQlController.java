package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.domain.Lifecycle;
import io.apiplatform.controlplane.service.ApiService;
import io.apiplatform.controlplane.service.TelemetryService;
import io.apiplatform.controlplane.web.Dtos.*;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL reuses the same services (and therefore the same authorization checks) as REST: authorization must never
 * live only in a transport layer. Query depth is naturally bounded by the small schema; see docs/rest-vs-graphql.md.
 */
@Controller
public class GraphQlController {
    private final ApiService apis;
    private final TelemetryService telemetry;

    public GraphQlController(ApiService apis, TelemetryService telemetry) {
        this.apis = apis;
        this.telemetry = telemetry;
    }

    @QueryMapping
    public PageView<ApiView> apis(@Argument String q, @Argument String lifecycle, @Argument String owner,
                                  @Argument int page, @Argument int size) {
        Lifecycle l = lifecycle == null || lifecycle.isBlank() ? null : Lifecycle.valueOf(lifecycle.toUpperCase());
        return apis.search(q, l, owner, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name")));
    }

    @QueryMapping
    public ApiView api(@Argument Long id) {
        return apis.get(id);
    }

    @QueryMapping
    public TrafficSummary healthSummary(@Argument int windowMinutes) {
        return telemetry.summary(Math.min(Math.max(windowMinutes, 1), 1440));
    }

    @QueryMapping
    public List<EventView> recentEvents(@Argument String route, @Argument int limit) {
        return telemetry.events(null, route, null, PageRequest.of(0, Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Direction.DESC, "ts"))).content();
    }

    @SchemaMapping(typeName = "Api", field = "events")
    public List<EventView> apiEvents(ApiView api, @Argument int limit) {
        return telemetry.events(null, api.slug(), null, PageRequest.of(0, Math.min(Math.max(limit, 1), 50), Sort.by(Sort.Direction.DESC, "ts"))).content();
    }

    @SchemaMapping(typeName = "OperationalEvent", field = "ts")
    public String ts(EventView e) {
        return e.ts().toString();
    }

    @SchemaMapping(typeName = "Api", field = "lifecycle")
    public String lifecycle(ApiView a) {
        return a.lifecycle().name();
    }

    @SchemaMapping(typeName = "ApiVersion", field = "status")
    public String versionStatus(VersionView v) {
        return v.status().name();
    }

    @SchemaMapping(typeName = "HealthSummary", field = "routes")
    public List<TrafficRouteSummary> routes(TrafficSummary s) {
        return s.routes();
    }

    @SchemaMapping(typeName = "RouteHealth", field = "errors5xx")
    public long errors5xx(TrafficRouteSummary r) {
        return r.errors5xx();
    }
}
