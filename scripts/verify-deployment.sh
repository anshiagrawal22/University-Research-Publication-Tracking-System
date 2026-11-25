#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:${BACKEND_PORT:-8080}}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:${FRONTEND_PORT:-3000}}"
API_BASE="${FRONTEND_URL}/api"

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts=30
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$label is healthy ($url)"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: $label did not become healthy at $url"
  return 1
}

wait_for_url "${BACKEND_URL}/api/dashboard/summary" "Backend API"
wait_for_url "${FRONTEND_URL}/" "Frontend Nginx"

echo "==> Verifying publication security gates via ${API_BASE}..."

# 1. XSS payload must be blocked before entering the database (HTTP 400)
xss_status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${API_BASE}/publications" \
  -H "Content-Type: application/json" \
  -d '{"title":"<script>alert(1)</script> Quantum Paper","authors":["Ada Lovelace"],"year":2025,"department":"Physics","journal":"Nature"}')
if [ "$xss_status" != "400" ]; then
  echo "ERROR: Expected HTTP 400 for XSS payload, got $xss_status"
  exit 1
fi
echo "Gate check 1 passed: XSS payload rejected (HTTP 400)."

# 2. Untrusted venue must be blocked from going live (status=REJECTED, publish=422)
blocked_resp=$(curl -fsS -X POST "${API_BASE}/publications" \
  -H "Content-Type: application/json" \
  -d '{"title":"Smoke Test Blocked Preprint Submission","authors":["Ada Lovelace"],"year":2025,"department":"Physics","journal":"Unreviewed Preprint Series"}')
blocked_id=$(python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="REJECTED" and d["gateStatus"]=="FAILED", d; print(d["id"])' <<<"$blocked_resp")
publish_blocked_status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${API_BASE}/publications/${blocked_id}/publish")
curl -fsS -X DELETE "${API_BASE}/publications/${blocked_id}" >/dev/null
if [ "$publish_blocked_status" != "422" ]; then
  echo "ERROR: Expected HTTP 422 when publishing blocked paper, got $publish_blocked_status"
  exit 1
fi
echo "Gate check 2 passed: Untrusted paper blocked from going live (HTTP 422)."

# 3. Valid paper must stage as IN_REVIEW and go VERIFIED only via Security Gate publish
valid_resp=$(curl -fsS -X POST "${API_BASE}/publications" \
  -H "Content-Type: application/json" \
  -d '{"title":"Smoke Test Verified Campus Research Paper","authors":["Ada Lovelace"],"doi":"10.5555/smoke.gate.2025.001","year":2025,"department":"Computer Science","journal":"IEEE Access","orcidId":"0000-0002-1825-0097"}')
valid_id=$(python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="IN_REVIEW" and d["gateStatus"]=="PASSED", d; print(d["id"])' <<<"$valid_resp")

live_resp=$(curl -fsS -X POST "${API_BASE}/publications/${valid_id}/publish")
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["status"]=="VERIFIED" and d["gateStatus"]=="PASSED", d' <<<"$live_resp"
curl -fsS -X DELETE "${API_BASE}/publications/${valid_id}" >/dev/null
echo "Gate check 3 passed: Valid paper staged IN_REVIEW and published live (VERIFIED) through Security Gate."

echo "==> Deployment and Security Gate verification succeeded."
