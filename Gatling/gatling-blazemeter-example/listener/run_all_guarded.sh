#!/usr/bin/env bash
# Run all 7 Gatling CVU tests sequentially, each under the Max Users guard.
# Tests whose CVU exceeds the threshold are aborted mid-run.
#
#   ./run_all_guarded.sh                 # threshold 10, terminate on breach
#   THRESHOLD=20 ACTION=stop ./run_all_guarded.sh
#   ./run_all_guarded.sh 15802012        # just one test id
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARD="$HERE/bzm_max_users_guard.py"
THRESHOLD="${THRESHOLD:-10}"
INTERVAL="${INTERVAL:-5}"
ACTION="${ACTION:-terminate}"
CSV="${CSV:-$HERE/guard-samples.csv}"

# project 2607423 "Gatling", workspace 2194183 "Ben T"
# id                name                        expected CVU
ALL_TESTS=(
  "15801953|1 ClosedAtOnceLoop (control)|5"
  "15801954|2 ClosedConstantConcurrent|5"
  "15801955|3 OpenConstantArrival|8"
  "15802012|4 OpenForeverThrottled|180"
  "15802017|5 RpsTargetOpen|9"
  "15802025|6 ThrottleBelowDemand|179"
  "15802050|7 BurstThrottleRamp|55"
)

if [[ $# -gt 0 ]]; then
  SELECTED=()
  for want in "$@"; do
    for row in "${ALL_TESTS[@]}"; do
      [[ "${row%%|*}" == "$want" ]] && SELECTED+=("$row")
    done
  done
else
  SELECTED=("${ALL_TESTS[@]}")
fi

echo "== guard sweep: threshold >$THRESHOLD VU, poll ${INTERVAL}s, on breach: $ACTION =="
declare -a RESULTS

for row in "${SELECTED[@]}"; do
  IFS='|' read -r id name expected <<<"$row"
  echo
  echo "──────── $name (test $id, expected CVU ~$expected) ────────"
  python3 "$GUARD" --test-id "$id" --threshold "$THRESHOLD" \
    --interval "$INTERVAL" --action "$ACTION" --csv "$CSV"
  case $? in
    0) RESULTS+=("$name : completed (never exceeded $THRESHOLD)") ;;
    2) RESULTS+=("$name : ABORTED by guard") ;;
    *) RESULTS+=("$name : ERROR") ;;
  esac
done

echo
echo "==================== summary ===================="
printf '%s\n' "${RESULTS[@]}"
echo "samples: $CSV"
