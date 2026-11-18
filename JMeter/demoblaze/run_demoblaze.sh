#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_PLAN="$SCRIPT_DIR/demoblaze.jmx"
RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/results}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RESULTS_FILE="$RESULTS_DIR/demoblaze-$TIMESTAMP.jtl"
LOG_FILE="$RESULTS_DIR/demoblaze-$TIMESTAMP.log"
JMETER_BIN="${JMETER_BIN:-jmeter}"

resolve_jmeter() {
    if [[ "$JMETER_BIN" == */* ]]; then
        [[ -x "$JMETER_BIN" ]]
        return
    fi

    local resolved
    resolved="$(command -v "$JMETER_BIN" 2>/dev/null || true)"
    if [[ -n "$resolved" ]]; then
        JMETER_BIN="$resolved"
        return 0
    fi

    for candidate in \
        "$HOME/Downloads/apache-jmeter-5.6.3/bin/jmeter" \
        "$HOME/apache-jmeter-5.6.3/bin/jmeter"; do
        if [[ -x "$candidate" ]]; then
            JMETER_BIN="$candidate"
            return 0
        fi
    done

    return 1
}

if ! resolve_jmeter; then
    printf 'JMeter CLI was not found. Set JMETER_BIN to the jmeter executable.\n' >&2
    exit 1
fi

mkdir -p "$RESULTS_DIR"

"$JMETER_BIN" \
    -n \
    -t "$TEST_PLAN" \
    -l "$RESULTS_FILE" \
    -j "$LOG_FILE" \
    "$@"

printf 'Results: %s\nLog: %s\n' "$RESULTS_FILE" "$LOG_FILE"