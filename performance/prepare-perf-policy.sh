#!/usr/bin/env bash
# Raises the orders API rate limit so performance tests measure the gateway, not the limiter's 429 path.
# Goes through the normal reviewed workflow (submit by admin, approve by a different user). Dummy local credentials.
set -euo pipefail
CP="${CP:-http://localhost:8080}"
PASSWORD="${DEV_AUTH_PASSWORD:-local-demo-password}"
token() { curl -s -X POST "$CP/api/v1/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'; }
ADMIN=$(token admin); APPROVER=$(token approver)
API_ID=$(curl -s -H "Authorization: Bearer $ADMIN" "$CP/api/v1/apis?q=orders" | sed -n 's/.*"content":\[{"id":\([0-9]*\).*/\1/p')
POLICY='{"auth":"API_KEY","allowedMethods":["GET","POST"],"rateLimitPerMinute":6000000,"burst":1000000,"rateAlgorithm":"TOKEN_BUCKET","timeoutMs":3000,"maxRequestBytes":65536,"retryEnabled":true}'
PID=$(curl -s -X POST "$CP/api/v1/apis/$API_ID/policies" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d "{\"config\":$POLICY,\"comment\":\"perf test: high limit\"}" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
curl -s -o /dev/null -X POST "$CP/api/v1/policies/$PID/approve" -H "Authorization: Bearer $APPROVER"
echo "perf policy v-id=$PID approved for api $API_ID; waiting for gateway propagation"
sleep 6
