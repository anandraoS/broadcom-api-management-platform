#!/usr/bin/env bash
# Prints the distinct metric names exposed by a service (default: gateway) - used to keep Grafana/alert expressions honest.
# Usage: scripts/list-metrics.sh [base-url] [grep-pattern]
BASE="${1:-http://localhost:8081}"
PATTERN="${2:-^(gateway_|hikaricp_connections|jvm_gc_pause|jvm_memory_used|process_cpu)}"
curl -s "$BASE/actuator/prometheus" | grep -v '^#' | sed 's/[{ ].*//' | sort -u | grep -E "$PATTERN"
