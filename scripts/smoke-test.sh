#!/usr/bin/env bash
# End-to-end smoke test against a RUNNING platform (scripts/run-local.sh start, or docker compose up).
# Exercises: gateway routing, authn/z, rate limiting, policy workflow + propagation delay, SSRF rejection, incidents.
# Prints PASS/FAIL per check and exits non-zero if any check fails. Dummy local credentials only.
set -uo pipefail
CP="${CP:-http://localhost:8080}"
GW="${GW:-http://localhost:8081}"
PASSWORD="${DEV_AUTH_PASSWORD:-local-demo-password}"
KEY="${DEMO_API_KEY:-kdemo.local-demo-only-key-0123456789abcdef}"
FAILS=0

check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1 ($3)"; else echo "FAIL  $1 expected=$2 actual=$3"; FAILS=$((FAILS+1)); fi
}
contains() { # name needle haystack
  if printf '%s' "$3" | grep -q -- "$2"; then echo "PASS  $1"; else echo "FAIL  $1 (missing '$2')"; FAILS=$((FAILS+1)); fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
token() { curl -s -X POST "$CP/api/v1/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'; }
now_ms() { date +%s%3N; }

ADMIN=$(token admin); APPROVER=$(token approver); OPERATOR=$(token operator)
check "token issued for admin" "yes" "$([ -n "$ADMIN" ] && echo yes || echo no)"

echo "--- gateway routing and authentication"
check "unknown route -> 404"                 404 "$(code "$GW/gw/does-not-exist/v1/x")"
check "no api key -> 401"                    401 "$(code "$GW/gw/orders/v1/orders")"
check "wrong api key -> 401"                 401 "$(code -H 'X-API-Key: kdemo.wrong' "$GW/gw/orders/v1/orders")"
check "valid api key -> 200"                 200 "$(code -H "X-API-Key: $KEY" "$GW/gw/orders/v1/orders")"
BODY=$(curl -s -H "X-API-Key: $KEY" -H 'X-Correlation-Id: smoke-corr-0001' -D /tmp/smoke_hdr "$GW/gw/orders/v1/orders/1")
contains "backend order returned through gateway" '"sku"' "$BODY"
contains "correlation id echoed" 'smoke-corr-0001' "$(cat /tmp/smoke_hdr)"
check "DELETE not allowed -> 405"            405 "$(code -X DELETE -H "X-API-Key: $KEY" "$GW/gw/orders/v1/orders/1")"
# --path-as-is: stop curl from normalising the dot-segments away before they reach the gateway
check "path traversal -> 400"                400 "$(code --path-as-is -H "X-API-Key: $KEY" "$GW/gw/orders/v1/%2e%2e/admin")"
check "oversized body -> 413"                413 "$(head -c 70000 /dev/zero | tr '\0' 'a' | curl -s -o /dev/null -w '%{http_code}' -X POST -H "X-API-Key: $KEY" -H 'Content-Type: text/plain' --data-binary @- "$GW/gw/orders/v1/orders")"

echo "--- load balancing across two targets"
SEEN=$(for i in 1 2 3 4 5 6; do curl -s -H "X-API-Key: $KEY" "$GW/gw/orders/v1/orders" | sed -n 's/.*"instance":"\([^"]*\)".*/\1/p'; done | sort -u | tr '\n' ' ')
contains "traffic reaches orders-1" "orders-1" "$SEEN"
contains "traffic reaches orders-2" "orders-2" "$SEEN"

echo "--- control-plane authorization"
check "anonymous API call -> 401"            401 "$(code "$CP/api/v1/apis")"
check "viewer cannot create API -> 403"      403 "$(code -X POST "$CP/api/v1/apis" -H "Authorization: Bearer $(token viewer)" -H 'Content-Type: application/json' -d '{"slug":"nope-api","name":"x","ownerTeam":"platform"}')"
API_ID=$(curl -s -H "Authorization: Bearer $ADMIN" "$CP/api/v1/apis?q=orders" | sed -n 's/.*"content":\[{"id":\([0-9]*\).*/\1/p')
check "orders API found in registry" "yes" "$([ -n "$API_ID" ] && echo yes || echo no)"
check "owner of other team cannot modify -> 403" 403 "$(code -X POST "$CP/api/v1/apis/$API_ID/lifecycle" -H "Authorization: Bearer $(token owner-payments)" -H 'Content-Type: application/json' -d '{"target":"INACTIVE"}')"

echo "--- SSRF: unsafe backend destinations are rejected"
VER_ID=$(curl -s -H "Authorization: Bearer $ADMIN" "$CP/api/v1/apis/$API_ID" | sed -n 's/.*"versions":\[{"id":\([0-9]*\).*/\1/p')
for target in "http://169.254.169.254" "http://evil.example.org" "file:///etc/passwd"; do
  check "reject target $target" 422 "$(code -X POST "$CP/api/v1/apis/$API_ID/versions/$VER_ID/targets" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "{\"url\":\"$target\",\"weight\":1}")"
done

echo "--- policy change: submit -> approve -> propagate -> enforce -> revert"
STRICT='{"auth":"API_KEY","allowedMethods":["GET","POST"],"rateLimitPerMinute":5,"burst":5,"rateAlgorithm":"FIXED_WINDOW","timeoutMs":3000,"maxRequestBytes":65536,"retryEnabled":true}'
INVALID='{"auth":"NONE","allowedMethods":["POST"],"rateLimitPerMinute":0,"burst":1,"rateAlgorithm":"FIXED_WINDOW","timeoutMs":3000,"maxRequestBytes":10,"retryEnabled":false}'
check "invalid policy rejected -> 422" 422 "$(code -X POST "$CP/api/v1/apis/$API_ID/policies" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "{\"config\":$INVALID}")"
PID=$(curl -s -X POST "$CP/api/v1/apis/$API_ID/policies" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "{\"config\":$STRICT,\"comment\":\"smoke: tighten limit\"}" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
check "policy submitted"               "yes" "$([ -n "$PID" ] && echo yes || echo no)"
check "self approval blocked -> 403"   403 "$(code -X POST "$CP/api/v1/policies/$PID/approve" -H "Authorization: Bearer $ADMIN")"
T0=$(now_ms)
check "approver approves -> 200"       200 "$(code -X POST "$CP/api/v1/policies/$PID/approve" -H "Authorization: Bearer $APPROVER")"
# poll the gateway until it enforces the new limit (6th request in a minute returns 429) and measure the delay
ENFORCED=""
for attempt in $(seq 1 40); do
  # distinct consumer window: the previous requests above already consumed part of the old window, so count 429s
  R=$(code -H "X-API-Key: $KEY" "$GW/gw/orders/v1/orders")
  if [ "$R" = "429" ]; then ENFORCED=yes; break; fi
  sleep 0.5
done
T1=$(now_ms)
check "gateway enforces new rate limit" "yes" "${ENFORCED:-no}"
echo "INFO  observed approve->enforced delay: $((T1-T0)) ms (gateway polls every 2s locally; this is NOT instantaneous)"
check "revert to v1 -> 200" 200 "$(code -X POST "$CP/api/v1/apis/$API_ID/policies/revert" -H "Authorization: Bearer $APPROVER" -H 'Content-Type: application/json' -d '{"toVersionNo":1}')"

echo "--- audit and incidents"
AUDIT=$(curl -s -H "Authorization: Bearer $ADMIN" "$CP/api/v1/audit?entityType=API&entityId=$API_ID&size=50")
contains "audit has POLICY_APPROVED" "POLICY_APPROVED" "$AUDIT"
contains "audit has POLICY_REVERTED" "POLICY_REVERTED" "$AUDIT"
contains "audit has TARGET_REJECTED" "TARGET_REJECTED" "$AUDIT"
INC=$(curl -s -H "Authorization: Bearer $OPERATOR" "$CP/api/v1/incidents?windowMinutes=30")
contains "incident report finds synthetic 5xx spike"   "HIGH_5XX_RATE" "$INC"
contains "incident report includes suggested steps"   "suggestedSteps" "$INC"

echo "--- gateway metrics"
METRICS=$(curl -s "$GW/actuator/prometheus")
contains "route metrics exposed" 'gateway_requests_seconds_count' "$METRICS"
contains "rate-limit counter exposed" 'gateway_rate_limited_total' "$METRICS"

echo
if [ "$FAILS" -eq 0 ]; then echo "ALL CHECKS PASSED"; else echo "$FAILS CHECK(S) FAILED"; fi
exit "$FAILS"
