#!/bin/bash
# ────────────────────────────────────────────────────────────────
# Generate TypeScript types from the OpenAPI spec.
#
# Usage:
#   1. Start the backend: cd backend && ./gradlew bootRun
#   2. Run this script:   ./scripts/generate-api-types.sh
#
# The generated types land in frontend/src/api/generated/
# ────────────────────────────────────────────────────────────────
set -euo pipefail

SPEC_URL="${SPEC_URL:-http://localhost:8080/api-docs}"
OUT_DIR="frontend/src/api/generated"

echo "📥  Fetching OpenAPI spec from ${SPEC_URL} ..."
mkdir -p "$OUT_DIR"

# Save a copy of the spec for CI diffing
curl -sf "$SPEC_URL" | python3 -m json.tool > "$OUT_DIR/openapi.json" 2>/dev/null \
  || curl -sf "$SPEC_URL" > "$OUT_DIR/openapi.json"

echo "⚙️  Generating TypeScript types ..."
npx openapi-typescript "$OUT_DIR/openapi.json" -o "$OUT_DIR/api-types.ts"

echo ""
echo "✅  Generated types in $OUT_DIR/api-types.ts"
echo ""
echo "Next steps:"
echo "  - Import from 'api/generated/api-types' in your API client code"
echo "  - Run 'npx tsc --noEmit' to verify frontend types match backend spec"
