#!/usr/bin/env bash
# Starts the whole platform locally WITHOUT Docker: 2 sample backends, control plane (H2, dev identity), gateway.
# Usage: scripts/run-local.sh [start|stop|status]   (build first: mvn -Plocal-h2 -DskipTests package)
# Requires Java 21 on PATH (or JAVA_HOME set). All credentials below are dummy local-only values.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="$ROOT/.run"
mkdir -p "$RUN"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

export JWT_SECRET="${JWT_SECRET:-local-demo-only-secret-change-me-0123456789}"
export GATEWAY_SERVICE_TOKEN="${GATEWAY_SERVICE_TOKEN:-local-demo-gateway-token}"
export DEV_AUTH_PASSWORD="${DEV_AUTH_PASSWORD:-local-demo-password}"
export DEMO_API_KEY="${DEMO_API_KEY:-kdemo.local-demo-only-key-0123456789abcdef}"

start_one() { # name, logfile, env..., -- java args
  local name="$1"; shift
  ( "$@" > "$RUN/$name.log" 2>&1 & echo $! > "$RUN/$name.pid" )
  echo "started $name (pid $(cat "$RUN/$name.pid"))"
}

wait_http() { # url, seconds
  for _ in $(seq 1 "$2"); do
    if curl -fsS -o /dev/null "$1" 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo "timeout waiting for $1" >&2; return 1
}

case "${1:-start}" in
  start)
    start_one orders-1 env PORT=9001 INSTANCE_NAME=orders-1 "$JAVA_BIN" -jar "$ROOT"/sample-services/orders-service/target/orders-service-1.0.0.jar
    start_one orders-2 env PORT=9002 INSTANCE_NAME=orders-2 "$JAVA_BIN" -jar "$ROOT"/sample-services/orders-service/target/orders-service-1.0.0.jar
    start_one control-plane env SPRING_PROFILES_ACTIVE=local "$JAVA_BIN" -jar "$ROOT"/control-plane/target/control-plane-1.0.0.jar
    wait_http http://localhost:8080/actuator/health 90
    start_one gateway env ALLOW_LOOPBACK_BACKENDS=true ALLOWED_BACKEND_HOSTS=localhost SNAPSHOT_POLL_MS=2000 \
        HEALTH_CHECK_MS=3000 TELEMETRY_FLUSH_MS=10000 CONTROL_PLANE_URL=http://localhost:8080 \
        "$JAVA_BIN" -jar "$ROOT"/gateway/target/gateway-1.0.0.jar
    wait_http http://localhost:8081/actuator/health/readiness 90
    echo "ready: gateway http://localhost:8081  control-plane http://localhost:8080 (swagger-ui.html)"
    ;;
  stop)
    for p in "$RUN"/*.pid; do [ -f "$p" ] && kill "$(cat "$p")" 2>/dev/null && echo "stopped $(basename "$p" .pid)"; rm -f "$p"; done
    ;;
  status)
    for p in "$RUN"/*.pid; do [ -f "$p" ] && echo "$(basename "$p" .pid): pid $(cat "$p")"; done
    ;;
esac
