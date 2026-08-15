#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

failed=0
scan_paths=(
  app/build.gradle.kts
  app/src/main/java
  app/frontEnd/public
)

check_absent() {
  local description="$1"
  local pattern="$2"
  local matches

  matches="$(git grep -n -I -E "$pattern" -- "${scan_paths[@]}" || true)"
  if [[ -n "$matches" ]]; then
    echo "[privacy-check] ERROR: $description"
    echo "$matches"
    failed=1
  fi
}

if [[ -e app/src/main/java/com/minikano/f50_sms/utils/KanoReport.kt ]]; then
  echo "[privacy-check] ERROR: KanoReport.kt must remain deleted"
  failed=1
fi

check_absent \
  "the original telemetry endpoint or implementation was restored" \
  'ufi_tools_report|api\.kanokano\.cn|reportToServer|KanoReport'

check_absent \
  "the stable-device-ID message channel was restored" \
  'get_message/|set_read_message/'

check_absent \
  "a known runtime analytics SDK reference was introduced" \
  'FirebaseAnalytics|com\.google\.firebase\.analytics|io\.sentry|com\.bugly|com\.umeng|appsflyer|mixpanel'

check_absent \
  "automatic in-app update polling was restored" \
  'checkUpdateAction\([[:space:]]*true[[:space:]]*\)'

if (( failed != 0 )); then
  echo "[privacy-check] FAILED: refusing to build or publish this revision"
  exit 1
fi

echo "[privacy-check] PASS: no blocked telemetry markers were found"
