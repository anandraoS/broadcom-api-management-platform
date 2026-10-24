package io.apiplatform.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.controlplane.incident.IncidentRules;
import io.apiplatform.controlplane.web.Dtos.TrafficRouteSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentRulesTest {
    private TrafficRouteSummary route(long req, long e5, double avg, long rl, long auth, long timeouts) {
        return new TrafficRouteSummary("r", req, 0, e5, req == 0 ? 0 : (double) e5 / req, avg, (long) avg * 3, rl, auth, timeouts);
    }

    @Test
    void healthyRouteProducesNoFindings() {
        assertThat(IncidentRules.evaluate(List.of(route(1000, 5, 80, 0, 10, 0)))).isEmpty();
    }

    @Test
    void tinySamplesAreIgnored() {
        assertThat(IncidentRules.evaluate(List.of(route(10, 10, 5000, 10, 10, 10)))).isEmpty();
    }

    @Test
    void highFiveXxRateIsCriticalAboveQuarter() {
        var f = IncidentRules.evaluate(List.of(route(100, 30, 50, 0, 0, 0)));
        assertThat(f).extracting("rule").contains("HIGH_5XX_RATE");
        assertThat(f.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(f.get(0).evidence()).containsKeys("requests", "errors5xx", "errorRate");
        assertThat(f.get(0).suggestedSteps()).isNotEmpty();
    }

    @Test
    void latencyTimeoutsAuthAndRateLimitRulesFire() {
        var f = IncidentRules.evaluate(List.of(route(100, 0, 2500, 40, 30, 5)));
        assertThat(f).extracting("rule").containsExactlyInAnyOrder(
                "BACKEND_TIMEOUTS", "ELEVATED_LATENCY", "AUTH_FAILURE_SPIKE", "RATE_LIMIT_SPIKE");
    }

    @Test
    void eventTypesMapToFindingsAndUnknownTypesAreIgnored() {
        assertThat(IncidentRules.fromEventType("CIRCUIT_OPEN", "r", 2).rule()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        assertThat(IncidentRules.fromEventType("SNAPSHOT_STALE", null, 1).rule()).isEqualTo("CONFIG_STALE");
        assertThat(IncidentRules.fromEventType("INVALID_CONFIG", null, 1).rule()).isEqualTo("INVALID_GATEWAY_CONFIG");
        assertThat(IncidentRules.fromEventType("POOL_EXHAUSTED", null, 1).rule()).isEqualTo("CONNECTION_POOL_EXHAUSTION");
        assertThat(IncidentRules.fromEventType("QUEUE_BACKLOG", null, 1).rule()).isEqualTo("QUEUE_BACKLOG");
        assertThat(IncidentRules.fromEventType("SOMETHING_ELSE", null, 1)).isNull();
    }
}
