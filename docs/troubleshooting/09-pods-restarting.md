# 09 - Kubernetes pods repeatedly restarting

**Symptoms:** `kubectl get pods` shows `CrashLoopBackOff` or restart count climbing for `gateway`; ingress returns 503s during churn.

**Hypotheses:** (1) failing liveness probe (startup slower than allowed); (2) `OOMKilled` (memory limit vs heap + non-heap); (3) application fails at startup (missing Secret key, invalid config: e.g. `app.security.jwt-secret must be at least 32 bytes`); (4) readiness never true because no snapshot (control plane unreachable by NetworkPolicy) - *not* a restart but looks similar; (5) `readOnlyRootFilesystem` + app writing to a non-writable path; (6) image pull / arch mismatch; (7) node pressure eviction.

**Evidence:** `kubectl describe pod` (Last State reason/exit code, events), previous container logs, probe settings, resource usage vs limits, NetworkPolicy flows.

**Commands**
```bash
kubectl -n api-platform get pods -l app=gateway
kubectl -n api-platform describe pod <pod> | sed -n '/Last State/,/Ready/p;/Events/,$p'
kubectl -n api-platform logs <pod> --previous | tail -50
kubectl -n api-platform get secret platform-secrets -o json | jq '.data | keys'     # keys only - never print values
kubectl -n api-platform top pod -l app=gateway
```

**Root cause (exercise):** exit code 137 + `OOMKilled`: memory limit 384 Mi with `MaxRAMPercentage=75` leaves ~96 Mi for metaspace, threads, direct buffers and the snapshot cache; under load the container exceeded the limit although heap was fine.
**Corrective:** raise the limit to 512-768 Mi (manifests/chart use 512 Mi), keep requests ~ typical use, verify with a load test; do not raise the liveness failure threshold to "hide" crashes.
**Regression tests:** `kubeconform`/`helm template` assertions in CI that every workload has requests/limits and probes (policy-as-code, e.g. conftest/kyverno); startup test that fails fast on invalid secrets (`SecurityConfig.secretKey` throws).
**Prevention:** startupProbe for slow JVM starts, PDB + `maxUnavailable: 0` rollouts, resource sizing from measurements, alert on restart rate, `ExitOnOutOfMemoryError` plus heap-dump volume for post-mortems.
