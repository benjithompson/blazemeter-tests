#!/usr/bin/env bash
# Run the GitHub Actions workflow (.github/workflows/dbankdemo-webvitals.yml)
# locally in Docker with `act`, the same way GitHub would run it.
#
# Needs: act (brew install act), a running Docker engine, and a BlazeMeter
# api-key.json (default: repo root). Output: job status in the console, and
# web-vitals-report-<masterId>.html in this folder.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
KEY="${BLAZEMETER_API_KEY:-$REPO/api-key.json}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# act does not read Docker contexts (Colima, Docker Desktop): pass the socket.
export DOCKER_HOST="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"

# Secrets file in a temp folder, deleted on exit.
python3 - "$KEY" "$WORK/secrets" <<'PY'
import json, sys, os
d = json.load(open(sys.argv[1]))
fd = os.open(sys.argv[2], os.O_WRONLY | os.O_CREAT, 0o600)
os.write(fd, f"BLAZEMETER_API_KEY_ID={d['id']}\nBLAZEMETER_API_KEY_SECRET={d['secret']}\n".encode())
PY

cd "$REPO"
status=0
act workflow_dispatch \
  -W .github/workflows/dbankdemo-webvitals.yml \
  -P ubuntu-latest=catthehacker/ubuntu:act-latest \
  --container-daemon-socket - \
  --secret-file "$WORK/secrets" \
  --artifact-server-path "$WORK/artifacts" 2>&1 | tee "$WORK/act.log" || status=$?

for zip in "$WORK"/artifacts/*/web-vitals-report-*/*.zip; do
  [ -e "$zip" ] || continue
  id="$(basename "$zip" .zip | sed 's/web-vitals-report-//')"
  unzip -o -q "$zip" -d "$WORK/out"
  mv "$WORK/out/web-vitals-report.html" "$HERE/web-vitals-report-$id.html"
done

echo
echo "================ local CI result ================"
grep -E 'Job (succeeded|failed)' "$WORK/act.log" | sed 's/^/  /'
grep -oE 'https://a\.blazemeter\.com/app/#/masters/[0-9]+' "$WORK/act.log" | tail -1 | sed 's/^/  BlazeMeter report: /' || true
ls -1t "$HERE"/web-vitals-report-*.html 2>/dev/null | head -1 | sed 's/^/  Web vitals dashboard: /' || true
[ "$status" -eq 0 ] && echo "  Workflow: PASS" || echo "  Workflow: FAIL"
exit "$status"
