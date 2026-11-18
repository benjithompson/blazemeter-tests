# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Validate BlazeMeter's VU/CVU reporting accuracy for Gatling tests. A field report described a Gatling test that showed VUs increasing linearly for the whole test while request throughput and engine CPU/memory stayed flat — suggesting BlazeMeter counts *cumulative distinct Gatling user IDs* rather than *currently active users*.

**Hypothesis:** Gatling's open-model (`constantUsersPerSec`) and closed-model-with-churn (`constantConcurrentUsers`) injections create a new user ID for every arriving user. If BlazeMeter derives VUs from distinct user IDs in `simulation.log`, reported VUs climb linearly even when true concurrency is flat.

## Structure

- `simulations/` — three self-contained Scala Gatling simulations (~5 true concurrent users, 3 min each, target https://blazedemo.com):
  - `ClosedAtOnceLoop.scala` — control: `atOnceUsers(5)` + 3-min loop; only 5 user IDs ever exist. Expect flat VU=5 report.
  - `ClosedConstantConcurrent.scala` — `constantConcurrentUsers(5)`, short scenario; flat true concurrency 5 but hundreds of user IDs. Linear VU climb here = reporting bug confirmed.
  - `OpenConstantArrival.scala` — `constantUsersPerSec(2)`, short scenario (~360 total users, ~5 concurrent).
- `simulations/BurstThrottleRamp.scala` — a short burst behind a ramping throttle: a VU spike to ~56 that then drains.
- `taurus/` — one Taurus YAML per simulation (BlazeMeter runs Gatling via the Taurus `gatling` executor). Each BlazeMeter test = the YAML + its `.scala` file uploaded together.

## Interpreting results

Compare BlazeMeter's VU/users timeline chart (and CVU max) per test against the "true concurrency" column above. The pair (ClosedAtOnceLoop vs ClosedConstantConcurrent) isolates the variable: identical true concurrency and throughput, differing only in user-ID churn.

## BlazeMeter

- Account/workspace: **SE Demo Ben T**, project: **Gatling** (created via PAG BlazeMeter MCP).
- Tests are created/managed through the PAG MCP (`pag__explore` / `pag__execute` with the blazemeter server).
- Keep load small: ~5 VUs, 3 minutes — the goal is reporting validation, not load.

## Local run (optional)

No build tool checked in; simulations are single-file Scala DSL (Gatling 3.x). Run locally with a Gatling bundle (`gatling.sh -sf simulations -s <SimulationName>`) or via Taurus: `bzt taurus/<config>.yml` (with the `.scala` beside it or `script:` path adjusted).
