#!/usr/bin/env python3
"""
BlazeMeter Max Users (CVU) guard.

Polls a running master's summary statistics every N seconds and aborts the run
the moment reported Max Users (concurrency) exceeds a threshold.

  Poll   : GET  /api/v4/masters/{masterId}/reports/default/summary -> result.maxUsers
           https://help.blazemeter.com/apidocs/performance/masters_summary_statistics.htm
  Abort  : POST /api/v4/masters/{masterId}/terminate
           https://help.blazemeter.com/apidocs/performance/masters_terminate.htm
           POST /api/v4/masters/{masterId}/stop   (graceful; preserves results)

`maxUsers` is a running high-water mark for the execution, so once it breaches it
never falls back below the threshold -- exactly the semantics wanted for a guard.

Auth: BLAZEMETER_API_KEY is either "id:secret", or a path to a JSON file
{"id": ..., "secret": ...}. BLAZEMETER_API_SECRET is honoured if the key env
holds only the id.

Exit codes:
  0  run finished on its own, threshold never breached
  2  run aborted by this guard (threshold breached)
  3  operational failure (auth, API, start failed)
"""

import argparse
import base64
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "https://a.blazemeter.com/api/v4"
# Master statuses meaning "no longer running"
TERMINAL = {"ENDED", "ABORTED", "FAILED"}


# ---------------------------------------------------------------- auth / http

def load_auth():
    key = os.environ.get("BLAZEMETER_API_KEY")
    if not key:
        sys.exit("BLAZEMETER_API_KEY not set")
    if os.path.isfile(key):
        with open(key) as fh:
            data = json.load(fh)
        kid, secret = data["id"], data["secret"]
    elif ":" in key:
        kid, secret = key.split(":", 1)
    else:
        kid = key
        secret = os.environ.get("BLAZEMETER_API_SECRET")
        if not secret:
            sys.exit("BLAZEMETER_API_KEY has no secret and BLAZEMETER_API_SECRET is unset")
    return "Basic " + base64.b64encode(f"{kid}:{secret}".encode()).decode()


def call(auth, method, path, timeout=20):
    """Return (parsed_json, error_string). Never raises on HTTP/network error."""
    req = urllib.request.Request(
        BASE + path,
        method=method,
        headers={"Authorization": auth, "Accept": "application/json"},
    )
    if method == "POST":
        req.data = b""  # some proxies dislike a POST with no body
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.load(resp), None
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace")[:300]
        return None, f"HTTP {exc.code}: {body}"
    except Exception as exc:  # noqa: BLE001 - network flake must not kill the guard
        return None, f"{type(exc).__name__}: {exc}"


# ---------------------------------------------------------------- API helpers

def start_test(auth, test_id):
    res, err = call(auth, "POST", f"/tests/{test_id}/start")
    if err:
        sys.exit(f"[guard] failed to start test {test_id}: {err}")
    master_id = (res.get("result") or {}).get("id")
    if not master_id:
        sys.exit(f"[guard] start returned no master id: {json.dumps(res)[:300]}")
    return master_id


def get_max_users(auth, master_id):
    """(max_users, hits, err). max_users is None when the report has no data yet."""
    res, err = call(auth, "GET", f"/masters/{master_id}/reports/default/summary")
    if err:
        return None, None, err
    result = res.get("result") or {}
    summary = (result.get("summary") or [{}])[0]
    return result.get("maxUsers"), summary.get("hits"), None


def get_status(auth, master_id):
    res, err = call(auth, "GET", f"/masters/{master_id}/status")
    if err:
        return None, err
    return (res.get("result") or {}).get("status"), None


def abort(auth, master_id, action):
    """action: terminate | stop | stop-then-terminate."""
    sent = []
    if action in ("stop", "stop-then-terminate"):
        _, err = call(auth, "POST", f"/masters/{master_id}/stop")
        sent.append(f"stop{'' if not err else ' FAILED: ' + err}")
    if action == "terminate":
        _, err = call(auth, "POST", f"/masters/{master_id}/terminate")
        sent.append(f"terminate{'' if not err else ' FAILED: ' + err}")
    if action == "stop-then-terminate":
        # give the graceful stop a moment to archive results, then hard-kill
        time.sleep(10)
        status, _ = get_status(auth, master_id)
        if status not in TERMINAL:
            _, err = call(auth, "POST", f"/masters/{master_id}/terminate")
            sent.append(f"terminate{'' if not err else ' FAILED: ' + err}")
        else:
            sent.append(f"terminate skipped (already {status})")
    return ", ".join(sent)


# ---------------------------------------------------------------------- guard

def guard(auth, master_id, threshold, interval, action, csv_path, max_wait):
    url = f"https://a.blazemeter.com/app/#/masters/{master_id}"
    print(f"[guard] master {master_id} | threshold >{threshold} VU | poll {interval}s | on breach: {action}")
    print(f"[guard] {url}")

    writer = fh = None
    if csv_path:
        new = not os.path.exists(csv_path)
        fh = open(csv_path, "a", newline="")
        writer = csv.writer(fh)
        if new:
            writer.writerow(["ts", "elapsed_s", "master_id", "status", "max_users", "hits", "note"])

    started = time.time()
    breached = False
    peak = 0
    status = "PENDING"

    try:
        while True:
            elapsed = round(time.time() - started, 1)
            status, serr = get_status(auth, master_id)
            max_users, hits, merr = get_max_users(auth, master_id)
            note = "; ".join(x for x in (serr, merr) if x)

            if max_users is not None:
                peak = max(peak, max_users)

            shown = "-" if max_users is None else max_users
            print(f"[guard] t+{elapsed:>6}s  status={status or '?':<10} maxUsers={shown:<6} hits={hits if hits is not None else '-'}"
                  + (f"  ({note})" if note else ""))
            if writer:
                writer.writerow([int(time.time()), elapsed, master_id, status or "",
                                 "" if max_users is None else max_users,
                                 "" if hits is None else hits, note])
                fh.flush()

            if max_users is not None and max_users > threshold:
                print(f"[guard] *** BREACH: maxUsers={max_users} > {threshold} -- aborting ***")
                detail = abort(auth, master_id, action)
                print(f"[guard] abort issued: {detail}")
                if writer:
                    writer.writerow([int(time.time()), elapsed, master_id, "ABORTING",
                                     max_users, hits or "", f"breach>{threshold}: {detail}"])
                    fh.flush()
                breached = True
                break

            if status in TERMINAL:
                print(f"[guard] run reached {status}; peak maxUsers={peak}")
                break

            if max_wait and elapsed > max_wait:
                print(f"[guard] max-wait {max_wait}s exceeded; giving up (test left running)")
                break

            time.sleep(interval)
    except KeyboardInterrupt:
        print("\n[guard] interrupted; test left running")
        return 3
    finally:
        if fh:
            fh.close()

    print(f"[guard] result: {'ABORTED (breach)' if breached else 'completed'} | peak maxUsers={peak} | {url}")
    return 2 if breached else 0


# ----------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description="Abort a BlazeMeter run when Max Users exceeds a threshold.")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--test-id", help="test to start, then guard")
    src.add_argument("--master-id", help="attach to an already-running execution")
    ap.add_argument("--threshold", type=int, default=10, help="abort when maxUsers exceeds this (default 10)")
    ap.add_argument("--interval", type=float, default=5.0, help="poll seconds (default 5)")
    ap.add_argument("--action", choices=["terminate", "stop", "stop-then-terminate"],
                    default="terminate",
                    help="terminate = immediate, discards results (default); "
                         "stop = graceful, preserves the report; stop-then-terminate = stop, hard-kill after 10s")
    ap.add_argument("--csv", help="append per-poll samples to this CSV")
    ap.add_argument("--max-wait", type=float, default=1800, help="safety cap in seconds (default 1800, 0 = none)")
    args = ap.parse_args()

    auth = load_auth()
    master_id = args.master_id or start_test(auth, args.test_id)
    if args.test_id:
        print(f"[guard] started test {args.test_id} -> master {master_id}")
    return guard(auth, master_id, args.threshold, args.interval, args.action, args.csv, args.max_wait)


if __name__ == "__main__":
    sys.exit(main())
