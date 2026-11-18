#!/usr/bin/env python3
"""
BlazeMeter fleet watcher / Max Users guard.

Discovers every *currently running* execution in a workspace or an entire account,
reads each one's Max Users (concurrency), and optionally aborts any that exceed a
threshold. Repeats on an interval.

  Discover : GET  /api/v4/masters?workspaceId={id}&active=true
             GET  /api/v4/masters?accountId={id}&active=true
             -> ONE call per cycle regardless of how many workspaces the account has
  Read VU  : GET  /api/v4/masters/{masterId}/reports/default/summary -> result.maxUsers
  Abort    : POST /api/v4/masters/{masterId}/terminate   (or /stop)

Default is READ-ONLY (list what's running). Pass --guard to actually abort breaches.

Auth: BLAZEMETER_API_KEY is "id:secret" or a path to {"id":..., "secret":...} JSON.

Exit codes:
  0  clean (nothing aborted)
  2  at least one run was aborted by the guard
  3  operational failure
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
from concurrent.futures import ThreadPoolExecutor

BASE = "https://a.blazemeter.com/api/v4"
APP = "https://a.blazemeter.com/app/#/masters"


def load_auth():
    key = os.environ.get("BLAZEMETER_API_KEY")
    if not key:
        sys.exit("BLAZEMETER_API_KEY not set")
    if os.path.isfile(key):
        data = json.load(open(key))
        kid, secret = data["id"], data["secret"]
    elif ":" in key:
        kid, secret = key.split(":", 1)
    else:
        kid, secret = key, os.environ.get("BLAZEMETER_API_SECRET")
        if not secret:
            sys.exit("BLAZEMETER_API_KEY has no secret and BLAZEMETER_API_SECRET is unset")
    return "Basic " + base64.b64encode(f"{kid}:{secret}".encode()).decode()


def call(auth, method, path, timeout=25):
    req = urllib.request.Request(BASE + path, method=method,
                                 headers={"Authorization": auth, "Accept": "application/json"})
    if method == "POST":
        req.data = b""
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.load(resp), None
    except urllib.error.HTTPError as exc:
        return None, f"HTTP {exc.code}: {exc.read().decode('utf-8', 'replace')[:200]}"
    except Exception as exc:  # noqa: BLE001
        return None, f"{type(exc).__name__}: {exc}"


# ------------------------------------------------------------------ discovery

def list_active(auth, scope, scope_id):
    """One call. Returns (list_of_masters, err)."""
    param = "workspaceId" if scope == "workspace" else "accountId"
    res, err = call(auth, "GET", f"/masters?{param}={scope_id}&active=true")
    if err:
        return [], err
    return res.get("result") or [], None


def read_max_users(auth, master):
    mid = master["id"]
    res, err = call(auth, "GET", f"/masters/{mid}/reports/default/summary")
    row = {
        "master_id": mid,
        "name": master.get("name") or "",
        "test_id": master.get("testId"),
        "project_id": master.get("projectId"),
        "created": master.get("created"),
        "max_users": None,
        "hits": None,
        "err": err,
    }
    if not err:
        result = res.get("result") or {}
        row["max_users"] = result.get("maxUsers")
        row["hits"] = (result.get("summary") or [{}])[0].get("hits")
    return row


def abort(auth, master_id, action):
    if action == "terminate":
        _, err = call(auth, "POST", f"/masters/{master_id}/terminate")
        return "terminate" + ("" if not err else f" FAILED: {err}")
    _, err = call(auth, "POST", f"/masters/{master_id}/stop")
    out = "stop" + ("" if not err else f" FAILED: {err}")
    if action == "stop-then-terminate":
        time.sleep(10)
        st, _ = call(auth, "GET", f"/masters/{master_id}/status")
        status = ((st or {}).get("result") or {}).get("status")
        if status not in ("ENDED", "ABORTED", "FAILED"):
            _, e2 = call(auth, "POST", f"/masters/{master_id}/terminate")
            out += ", terminate" + ("" if not e2 else f" FAILED: {e2}")
        else:
            out += f", terminate skipped (already {status})"
    return out


# ---------------------------------------------------------------------- cycle

def cycle(auth, scope, scope_id, threshold, do_guard, action, workers, writer, aborted):
    t0 = time.time()
    actives, err = list_active(auth, scope, scope_id)
    t_discover = time.time() - t0
    if err:
        print(f"  ! discovery failed: {err}")
        return False

    # don't re-poll something we already killed this session
    pending = [m for m in actives if m["id"] not in aborted]
    t1 = time.time()
    if pending:
        with ThreadPoolExecutor(max_workers=min(workers, len(pending))) as ex:
            rows = list(ex.map(lambda m: read_max_users(auth, m), pending))
    else:
        rows = []
    t_poll = time.time() - t1

    stamp = time.strftime("%H:%M:%S")
    print(f"[{stamp}] {len(actives)} running in {scope} {scope_id}"
          f"  (discover {t_discover:.2f}s, poll {len(pending)}x {t_poll:.2f}s)")

    if not actives:
        print("        (none)")

    breached_now = False
    for r in sorted(rows, key=lambda x: -(x["max_users"] or 0)):
        vu = r["max_users"]
        over = vu is not None and vu > threshold
        mark = "!!" if over else "  "
        shown = "-" if vu is None else vu
        print(f"   {mark} {str(r['master_id']):<10} maxUsers={str(shown):<6} hits={r['hits'] if r['hits'] is not None else '-':<7}"
              f" {r['name'][:52]}"
              + (f"   ({r['err']})" if r["err"] else ""))

        if writer:
            writer.writerow([int(time.time()), scope, scope_id, r["master_id"], r["name"],
                             r["test_id"], "" if vu is None else vu,
                             "" if r["hits"] is None else r["hits"], "OVER" if over else ""])

        if over and do_guard:
            detail = abort(auth, r["master_id"], action)
            aborted.add(r["master_id"])
            breached_now = True
            print(f"      *** ABORT {r['master_id']}: maxUsers={vu} > {threshold} -> {detail}")
            print(f"          {APP}/{r['master_id']}")
            if writer:
                writer.writerow([int(time.time()), scope, scope_id, r["master_id"], r["name"],
                                 r["test_id"], vu, r["hits"] or "", f"ABORTED: {detail}"])
        elif over:
            print(f"      (over threshold; --guard not set, leaving alone) {APP}/{r['master_id']}")

    return breached_now


def main():
    ap = argparse.ArgumentParser(description="List / guard all running BlazeMeter executions.")
    scope = ap.add_mutually_exclusive_group(required=True)
    scope.add_argument("--workspace-id")
    scope.add_argument("--account-id")
    ap.add_argument("--threshold", type=int, default=10, help="Max Users ceiling (default 10)")
    ap.add_argument("--interval", type=float, default=5.0, help="poll seconds (default 5)")
    ap.add_argument("--once", action="store_true", help="single pass, then exit")
    ap.add_argument("--guard", action="store_true",
                    help="actually abort breaches (default is read-only listing)")
    ap.add_argument("--action", choices=["terminate", "stop", "stop-then-terminate"],
                    default="terminate")
    ap.add_argument("--workers", type=int, default=10, help="parallel summary fetches (default 10)")
    ap.add_argument("--csv")
    ap.add_argument("--max-wait", type=float, default=0, help="stop watching after N seconds (0 = forever)")
    args = ap.parse_args()

    auth = load_auth()
    scope_name = "workspace" if args.workspace_id else "account"
    scope_id = args.workspace_id or args.account_id

    writer = fh = None
    if args.csv:
        new = not os.path.exists(args.csv)
        fh = open(args.csv, "a", newline="")
        writer = csv.writer(fh)
        if new:
            writer.writerow(["ts", "scope", "scope_id", "master_id", "name",
                             "test_id", "max_users", "hits", "note"])

    mode = f"GUARD (abort >{args.threshold} via {args.action})" if args.guard else f"watch only (flagging >{args.threshold})"
    print(f"== {scope_name} {scope_id} | {mode} | every {args.interval}s ==")

    aborted = set()
    started = time.time()
    try:
        while True:
            cycle(auth, scope_name, scope_id, args.threshold,
                  args.guard, args.action, args.workers, writer, aborted)
            if fh:
                fh.flush()
            if args.once:
                break
            if args.max_wait and time.time() - started > args.max_wait:
                print("[fleet] max-wait reached")
                break
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\n[fleet] stopped")
    finally:
        if fh:
            fh.close()

    if aborted:
        print(f"\n[fleet] aborted {len(aborted)} run(s): {sorted(aborted)}")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
