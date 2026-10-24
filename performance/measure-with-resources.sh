#!/usr/bin/env bash
# Runs LoadProbe against the gateway while sampling JVM process CPU and heap of gateway, control plane and backends
# via Spring Boot Actuator metrics. Output is raw measurement; interpret with the caveats in docs/performance.md.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
GW="${GW:-http://localhost:8081}"
KEY="${DEMO_API_KEY:-kdemo.local-demo-only-key-0123456789abcdef}"
C="${1:-32}"; S="${2:-15}"

# Only /actuator/prometheus is exposed, so read the scrape text. process_cpu_usage is 0..1 of ALL cores (8 here).
cpu()  { curl -s "$1/actuator/prometheus" | sed -n 's/^process_cpu_usage[^ ]* \([0-9.eE+-]*\)$/\1/p' | head -1; }
heap() { curl -s "$1/actuator/prometheus" | grep '^jvm_memory_used_bytes{.*area="heap"' | sed 's/.* //' \
         | awk '{ s += $1 } END { printf "%.0f", s / 1048576 }'; }
sample() {
  for _ in $(seq 1 $((S/3))); do
    sleep 3
    printf 'sample: process_cpu_usage gateway=%s controlplane=%s orders1=%s orders2=%s | heap MiB gateway=%s orders1=%s\n' \
      "$(cpu "$GW")" "$(cpu http://localhost:8080)" "$(cpu http://localhost:9001)" "$(cpu http://localhost:9002)" \
      "$(heap "$GW")" "$(heap http://localhost:9001)"
  done
}
sample &
SAMPLER=$!
"$JAVA_BIN" "$HERE/LoadProbe.java" "$GW/gw/orders/v1/orders/1" "$C" "$S" "X-API-Key:$KEY"
wait "$SAMPLER" 2>/dev/null
