#!/usr/bin/env bash
set -euo pipefail

echo "==> Running repository secret and credential hygiene scan..."
if grep -rnE \
  --exclude-dir=.git \
  --exclude-dir=node_modules \
  --exclude-dir=target \
  --exclude-dir=dist \
  --exclude='security-scan.sh' \
  '(AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{36}|xox[baprs]-[A-Za-z0-9-]{10,})' \
  . ; then
  echo "ERROR: Potential hardcoded secret or private key detected!"
  exit 1
fi
echo "Secret scan passed."

if [ -f frontend/package-lock.json ] && command -v npm >/dev/null 2>&1; then
  echo "==> Running frontend production dependency audit (critical)..."
  (cd frontend && npm audit --omit=dev --audit-level=critical)
fi

echo "==> All static security gate checks passed."
