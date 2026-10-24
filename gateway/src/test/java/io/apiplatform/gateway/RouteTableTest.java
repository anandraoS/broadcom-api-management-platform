package io.apiplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.gateway.auth.IpAllowList;
import io.apiplatform.gateway.config.RouteTable;
import io.apiplatform.gateway.config.Snapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTableTest {
    private Snapshot.Route route(String base) {
        return new Snapshot.Route("a" + base.hashCode(), "v1", base, List.of(new Snapshot.Target("http://localhost:1", 1, "/h")), null, 1);
    }

    @Test
    void longestPrefixWinsAndRemainderIsReturned() {
        var t = new RouteTable(List.of(route("/orders/v1"), route("/orders/v1/admin"), route("/billing/v2")), "t");
        var m = t.match("/orders/v1/items/5").orElseThrow();
        assertThat(m.route().basePath()).isEqualTo("/orders/v1");
        assertThat(m.remainder()).isEqualTo("/items/5");
        assertThat(t.match("/orders/v1/admin/users").orElseThrow().route().basePath()).isEqualTo("/orders/v1/admin");
        assertThat(t.match("/orders/v1").orElseThrow().remainder()).isEmpty();
    }

    @Test
    void unknownPathsAndPartialSegmentsDoNotMatch() {
        var t = new RouteTable(List.of(route("/orders/v1")), "t");
        assertThat(t.match("/orders/v10/x")).isEmpty();   // /orders/v10 is not /orders/v1
        assertThat(t.match("/orders")).isEmpty();
        assertThat(t.match("/")).isEmpty();
        assertThat(t.match("")).isEmpty();
    }

    @Test
    void queryStringIsIgnoredForMatching() {
        var t = new RouteTable(List.of(route("/orders/v1")), "t");
        assertThat(t.match("/orders/v1/x?a=b").orElseThrow().remainder()).isEqualTo("/x");
    }

    @Test
    void lookupStaysFastWithTwentyThousandRoutes() {
        List<Snapshot.Route> routes = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) routes.add(route("/svc" + i + "/v1"));
        var t = new RouteTable(routes, "big");
        for (int i = 0; i < 50_000; i++) t.match("/svc" + (i % 20_000) + "/v1/items/1"); // warm-up
        long start = System.nanoTime();
        int n = 200_000;
        for (int i = 0; i < n; i++) assertThat(t.match("/svc" + (i % 20_000) + "/v1/items/1")).isPresent();
        double perLookupNs = (System.nanoTime() - start) / (double) n;
        System.out.printf("RouteTable lookup with 20000 routes: %.0f ns/lookup (informational, includes string concat + assertion)%n", perLookupNs);
        assertThat(perLookupNs).isLessThan(50_000); // generous guard against accidental O(n) scans (O(n) would be ~ms)
    }

    @Test
    void ipAllowListMatchesCidrsAndFailsClosedOnGarbage() {
        assertThat(IpAllowList.allowed(null, "1.2.3.4")).isTrue();
        assertThat(IpAllowList.allowed(List.of(), "1.2.3.4")).isTrue();
        assertThat(IpAllowList.allowed(List.of("10.0.0.0/8"), "10.1.2.3")).isTrue();
        assertThat(IpAllowList.allowed(List.of("10.0.0.0/8"), "11.1.2.3")).isFalse();
        assertThat(IpAllowList.allowed(List.of("192.168.1.7/32"), "192.168.1.7")).isTrue();
        assertThat(IpAllowList.allowed(List.of("192.168.1.7/32"), "192.168.1.8")).isFalse();
        assertThat(IpAllowList.allowed(List.of("10.0.0.0/8"), "::1")).isFalse();
        assertThat(IpAllowList.allowed(List.of("not-a-cidr"), "10.1.1.1")).isFalse();
        assertThat(IpAllowList.allowed(List.of("10.0.0.0/8"), null)).isFalse();
    }
}
