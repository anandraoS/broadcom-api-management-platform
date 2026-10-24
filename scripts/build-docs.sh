#!/usr/bin/env bash
# Regenerates both PDFs from the structured content in docs/pdf/generator (Node + pdfkit; no network needed after npm ci).
# Usage: scripts/build-docs.sh [arch|guide|all]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/docs/pdf/generator"
[ -d node_modules ] || npm ci --no-audit --no-fund
node build.js "${1:-all}"
ls -l "$ROOT/docs/pdf"/*.pdf
