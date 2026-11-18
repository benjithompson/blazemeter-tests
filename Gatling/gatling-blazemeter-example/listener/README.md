# Max Users guard (BlazeMeter API listener)

Polls a running BlazeMeter execution every 5 s and aborts it the moment reported
**Max Users (concurrency)** exceeds a threshold. Built for the CVU validation suite in
this repo — tests 4, 6 and 7 are known to blow past 10 VU and should be killed mid-run.

## API surface

| Purpose | Call |
|---|---|
| Read Max Users | `GET /api/v4/masters/{masterId}/reports/default/summary` → `result.maxUsers` ([docs](https://help.blazemeter.com/apidocs/performance/masters_summary_statistics.htm)) |
| Run state | `GET /api/v4/masters/{masterId}/status` → `result.status` |
| Hard abort | `POST /api/v4/masters/{masterId}/terminate` ([docs](https://help.blazemeter.com/apidocs/performance/masters_terminate.htm)) |
| Graceful abort | `POST /api/v4/masters/{masterId}/stop` |
| Start a test | `POST /api/v4/tests/{testId}/start` → `result.id` = masterId |

Auth is HTTP Basic `api_key_id:api_key_secret`.

**Is it real time?** Near enough. The summary report is served from BlazeMeter's live
aggregation while the run is in progress; observed lag is a few seconds behind the
engine, so a 5 s poll gives ~5–15 s worst-case detection latency. `maxUsers` is a
running **high-water mark** — it only ever climbs — which is the right semantic for a
guard: once breached it cannot un-breach, so there is no flapping and no need for
consecutive-sample confirmation.

## Usage

```bash
export BLAZEMETER_API_KEY=/path/to/api-key.json     # {"id":..., "secret":...}
# or: export BLAZEMETER_API_KEY='id:secret'

# start a test and guard it
python3 bzm_max_users_guard.py --test-id 15802012 --threshold 10 --interval 5

# attach to a run that's already going
python3 bzm_max_users_guard.py --master-id 82849592 --threshold 10

# all 7 CVU tests, sequentially
./run_all_guarded.sh
THRESHOLD=20 ACTION=stop ./run_all_guarded.sh
./run_all_guarded.sh 15802012 15802025          # subset by test id
```

### Abort mode (`--action`)

| Value | Behaviour |
|---|---|
| `terminate` (default) | Immediate halt. Fastest, but **discards artifacts** — logs and files are not archived. |
| `stop` | Graceful shutdown; the report is preserved. Takes longer to actually stop. |
| `stop-then-terminate` | `stop`, then hard-kill 10 s later if still running. Best of both if you want the chart afterwards. |

BlazeMeter's own docs recommend `stop` to avoid losing results. Use it if you want to
inspect the VU timeline of an aborted run.

### Exit codes

`0` completed without breach · `2` aborted by the guard · `3` operational failure

### Output

Per-poll lines to stdout, plus an optional `--csv` sample log
(`ts, elapsed_s, master_id, status, max_users, hits, note`) — useful for plotting when
the guard fires against the CVU-accumulation tests.

## Expected results for this repo's suite

| # | Test | id | Peak CVU | Guard @10 |
|---|---|---|---|---|
| 1 | ClosedAtOnceLoop (control) | 15801953 | 5 | completes |
| 2 | ClosedConstantConcurrent | 15801954 | 5 | completes |
| 3 | OpenConstantArrival | 15801955 | 8 | completes |
| 4 | OpenForeverThrottled | 15802012 | 180 | **aborted** |
| 5 | RpsTargetOpen | 15802017 | 9 | completes |
| 6 | ThrottleBelowDemand | 15802025 | 179 | **aborted** |
| 7 | BurstThrottleRamp | 15802050 | 55 | **aborted** |

Tests 4 and 6 accumulate ~1 VU/s, so they cross 10 within ~10–15 s of traffic. Test 7
spikes to ~55 in under 10 s — the guard catches it, but the spike is faster than the
poll interval, so expect the abort to land with `maxUsers` already well past 10 rather
than at exactly 11.

## Fleet mode — every running test in a workspace or account

`bzm_fleet_guard.py` discovers all *currently running* executions and reads Max Users for
each. **Read-only by default**; pass `--guard` to actually abort breaches.

```bash
# what's running right now in SE Demo Ben T
python3 bzm_fleet_guard.py --workspace-id 2194183 --once

# whole account, one pass
python3 bzm_fleet_guard.py --account-id 291446 --once

# continuous watch, abort anything over 10 VU
python3 bzm_fleet_guard.py --workspace-id 2194183 --guard --threshold 10 --interval 5 \
  --action stop-then-terminate --csv fleet.csv
```

Discovery is `GET /api/v4/masters?workspaceId={id}&active=true` (or `accountId=`) — an
undocumented but working filter that returns only live masters. Already-aborted masters
are remembered so they aren't re-polled.

### Account-wide feasibility — measured

| Call | Time |
|---|---|
| Discovery (`active=true`), workspace scope | 0.41 – 0.50 s |
| Discovery (`active=true`), **account** scope, 166 workspaces | 0.39 – 0.49 s |
| One `summary` read | ~0.45 s (occasional 1.3 s outlier) |
| 20 `summary` reads, 10 threads | 2.17 s |

**Account-wide is entirely feasible.** The key finding: `accountId=…&active=true` is a
**single call** that costs the same as the workspace query — it does *not* fan out over
the 166 workspaces in the account. Per-cycle cost is therefore:

```
0.45s (discovery)  +  ceil(N_active / workers) × ~0.5s
```

With the default 10 workers, a 5 s poll interval absorbs roughly **80 concurrently
running tests** before cycles start overlapping. Realistically an account never has that
many live at once, so a 5 s account-wide guard has ample headroom. Raise `--workers` if
it ever does.

Caveat: this account has 166 workspaces and the guard would police *all* of them — it
will abort other people's runs. Scope to a workspace unless account-wide enforcement is
genuinely what you want.

## Validation runs

| Test | Master | Result |
|---|---|---|
| 4 OpenForeverThrottled (CVU 180) | [82859417](https://a.blazemeter.com/app/#/masters/82859417) | breach at `maxUsers=15` t+144.9s → terminated. Stopped at 24 s / 199 hits instead of 180 CVU over 3 min. |
| 1 ClosedAtOnceLoop (control, CVU 5) | [82859485](https://a.blazemeter.com/app/#/masters/82859485) | `maxUsers=5` flat for the full run → `ENDED`, exit 0. No false positive. |

Timing observed on both: ~90–120 s boot (`CREATE` → `BOOT_STARTING` → `INIT_SCRIPT` →
`TAURUS_ENGINE_READY` → `DATA_RECEIVED`), then the summary report stays empty a further
~20 s after `DATA_RECEIVED` before the first `maxUsers` lands. Detection was ~6 s after
first data on test 4.

## Notes / limitations

- **Overshoot is expected.** Test 4 breached at 15 and settled at 25 after the terminate,
  because of report lag + 5 s polling + tail data flushing post-abort. The guard is a
  circuit breaker, not a precise cap. Test 7 (0 → 55 VU in under 10 s) will overshoot most.
- The `/api/v4/data/kpis` endpoint (per-interval `na` = active users) would give a finer
  timeline, but it needs session ids and returned empty for these runs — `maxUsers` from
  the summary report is the reliable source.
- Boot time (queued → downloading → running) is 1–2 min; during that window the summary
  report has no data and the guard prints `maxUsers=-` while waiting.
- `--max-wait` (default 1800 s) is a safety cap so the guard can't poll forever if a run
  hangs. On timeout the guard exits and **leaves the test running**.
