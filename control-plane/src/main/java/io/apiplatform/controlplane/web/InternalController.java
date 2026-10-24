package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.service.GatewaySnapshot;
import io.apiplatform.controlplane.service.SnapshotService;
import io.apiplatform.controlplane.service.TelemetryService;
import io.apiplatform.controlplane.web.Dtos.TelemetryBatch;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Data-plane facing endpoints. Authenticated by GatewayTokenFilter (ROLE_GATEWAY only). */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {
    private final SnapshotService snapshots;
    private final TelemetryService telemetry;

    public InternalController(SnapshotService snapshots, TelemetryService telemetry) {
        this.snapshots = snapshots;
        this.telemetry = telemetry;
    }

    /** Conditional GET: gateways send If-None-Match with the last snapshot version and receive 304 if unchanged. */
    @GetMapping("/gateway/snapshot")
    public ResponseEntity<GatewaySnapshot> snapshot(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String inm) {
        GatewaySnapshot s = snapshots.current();
        String etag = "\"" + s.version() + "\"";
        if (etag.equals(inm)) return ResponseEntity.status(304).eTag(etag).build();
        return ResponseEntity.ok().eTag(etag).body(s);
    }

    @PostMapping("/telemetry/batches")
    public ResponseEntity<Map<String, Object>> telemetry(@Valid @RequestBody TelemetryBatch batch) {
        int stored = telemetry.ingest(batch);
        return ResponseEntity.accepted().body(Map.of("batchId", batch.batchId(), "storedBuckets", stored));
    }
}
