package io.apiplatform.gateway.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, per-snapshot lookup structure. Matching walks the request path from the longest prefix to the shortest
 * and does one hash lookup per path segment: O(segments), independent of the number of routes
 * (RouteTableTest prints a 20,000-route timing as evidence; it is not a benchmark guarantee).
 */
public final class RouteTable {
    private final Map<String, Snapshot.Route> byBasePath;
    private final String version;
    private static final RouteTable EMPTY = new RouteTable(List.of(), "empty");

    public RouteTable(List<Snapshot.Route> routes, String version) {
        Map<String, Snapshot.Route> m = new HashMap<>();
        for (Snapshot.Route r : routes) m.put(r.basePath(), r);
        this.byBasePath = Map.copyOf(m);
        this.version = version;
    }

    public static RouteTable empty() { return EMPTY; }

    public String version() { return version; }

    public int size() { return byBasePath.size(); }

    public Optional<Match> match(String gatewayPath) {
        // gatewayPath is the part after "/gw", e.g. /orders/v1/items/5
        String p = gatewayPath;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        String candidate = p;
        while (!candidate.isEmpty()) {
            Snapshot.Route r = byBasePath.get(candidate);
            if (r != null) return Optional.of(new Match(r, p.substring(candidate.length())));
            int slash = candidate.lastIndexOf('/');
            if (slash <= 0) break;
            candidate = candidate.substring(0, slash);
        }
        return Optional.empty();
    }

    /** remainder: path after the base path (path rewriting: the base path is stripped before forwarding). */
    public record Match(Snapshot.Route route, String remainder) {}
}
