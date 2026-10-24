package io.apiplatform.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Captures EXPLAIN output for the hot queries and asserts they use their intended indexes. NOTE: this runs on H2
 * (MySQL mode) with a few thousand synthetic rows - the plan SHAPE (index vs full scan) is meaningful, the costs are not
 * MySQL's. Re-run EXPLAIN / EXPLAIN ANALYZE on MySQL 8 with production-like data before relying on a plan
 * (docs/database-performance.md). Output is written to target/query-plans.txt.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueryPlanIT {
    @Autowired JdbcTemplate jdbc;

    private String plan(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList("EXPLAIN " + sql, args);
        StringBuilder sb = new StringBuilder("-- " + sql.replaceAll("\\s+", " ") + "\n");
        rows.forEach(r -> sb.append(r.values().iterator().next()).append('\n'));
        return sb.toString();
    }

    @Test
    void hotQueriesUseTheirIndexes() throws Exception {
        for (int i = 0; i < 3000; i++) {
            jdbc.update("insert into traffic_bucket(batch_id, route_slug, bucket_start, requests, errors_4xx, errors_5xx, rate_limited, "
                    + "auth_failures, timeouts, latency_sum_ms, latency_max_ms) values (?,?,dateadd('MINUTE', ?, current_timestamp),1,0,0,0,0,0,10,10)",
                    "plan-" + i, "route-" + (i % 20), -i);
            jdbc.update("insert into audit_event(ts, actor, action, entity_type, entity_id) values (dateadd('MINUTE', ?, current_timestamp),'a','X','API',?)",
                    -i, String.valueOf(i % 50));
        }
        jdbc.execute("analyze");
        String trafficPlan = plan("select * from traffic_bucket where route_slug = ? and bucket_start > dateadd('MINUTE', -15, current_timestamp) order by bucket_start", "route-3");
        String auditPlan = plan("select * from audit_event where entity_type = ? and entity_id = ? order by ts desc limit 25", "API", "7");
        String apiPlan = plan("select * from api_definition where owner_team = ? and lifecycle = ?", "orders", "ACTIVE");
        Files.writeString(Path.of("target", "query-plans.txt"), trafficPlan + "\n" + auditPlan + "\n" + apiPlan);

        assertThat(trafficPlan.toLowerCase()).contains("idx_bucket_route_time");
        assertThat(auditPlan.toLowerCase()).contains("idx_audit_entity_ts"); // V2 migration: filter + order served by one index
        assertThat(apiPlan.toLowerCase()).contains("idx_api_owner_lifecycle");
    }
}
