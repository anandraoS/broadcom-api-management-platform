#!/usr/bin/env bash
# Runs the dependency-free LoadProbe against a running local platform and prints measured results.
# Usage: performance/run-probes.sh [concurrency] [seconds]. Needs Java 21 on PATH (or JAVA_HOME) and curl.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
C="${1:-32}"; S="${2:-15}"
CP="${CP:-http://localhost:8080}"; GW="${GW:-http://localhost:8081}"
PASSWORD="${DEV_AUTH_PASSWORD:-local-demo-password}"
KEY="${DEMO_API_KEY:-kdemo.local-demo-only-key-0123456789abcdef}"
TOKEN=$(curl -s -X POST "$CP/api/v1/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"viewer\",\"password\":\"$PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

echo "== control plane: GET /api/v1/apis (JWT validation + JPA query + pagination)"
"$JAVA_BIN" "$HERE/LoadProbe.java" "$CP/api/v1/apis?size=20" "$((C/2))" "$S" "Authorization:Bearer $TOKEN"
echo "== control plane: GET /actuator/health/liveness (no auth, no DB) - framework floor"
"$JAVA_BIN" "$HERE/LoadProbe.java" "$CP/actuator/health/liveness" "$((C/2))" "$S"
