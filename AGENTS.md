# AGENTS.md

## What this repo is

This repo is a collection of example and demo tests for BlazeMeter. It is not one
application. Each top-level folder holds standalone examples for one test
executor. The examples exist to show how a script is written, how Taurus wraps
it, and how it runs on BlazeMeter.

**Every test here is intended to run on BlazeMeter.** Local runs are only a way
to check a script before you upload it. When you add or change an example, keep
it compatible with a BlazeMeter execution:

- Give the example a Taurus YAML, because BlazeMeter runs Taurus.
- Use relative paths for data files and imports. The cloud engine has a
  different working directory than your machine.
- Do not depend on a local install, a local port, or a file outside the example
  folder.
- Keep the asset set small enough to upload.

Treat every subfolder as an independent project. Do not refactor across folders.
Do not assume a shared build, a shared dependency set, or a shared style.

| Folder | Executor | Typical contents |
| --- | --- | --- |
| `JMeter/` | Apache JMeter | `.jmx` plans, CSV data, plugins, recordings |
| `Gatling/` | Gatling | Scala and Java simulations, sbt project, Taurus YAML |
| `K6/` | Grafana k6 | JS and TS scripts, Taurus YAML |
| `Locust/` | Locust | Python locustfiles, Taurus YAML |
| `Playwright/` | Playwright | Node projects, reports, artifacts |
| `Selenium/` | Selenium | Java, TestNG, Chrome Recorder exports |
| `Mocha/` | Mocha | JS tests, Taurus YAML |
| `Taurus/` | Taurus | YAML configs and templates that drive the above |

Some subfolders have their own `README.md` or `CLAUDE.md`. Read those first when
you work inside them. They take priority over this file.

`Selenium/TestNG/BlazeMeterTestNGExample` is a git submodule. It has its own
remote and its own history. Commit inside it first, push it, then commit the
new pointer in this repo.

```bash
git submodule update --init --recursive
```

## Python environment

Homebrew Python on this machine is externally managed (PEP 668). A plain
`pip install <package>` fails. Use the repo virtual environment instead.

The environment lives at `.venv/` in the repo root. It has Locust installed.

```bash
source .venv/bin/activate
```

To add a package:

```bash
.venv/bin/python -m pip install <package>
```

Never pass `--break-system-packages`, and never install into
`/opt/homebrew/bin/python3`.

## Running Locust

```bash
source .venv/bin/activate
locust -f Locust/demoblaze-browse-cart/demoblaze_locust.py
```

Headless, with a fixed load and duration:

```bash
locust -f Locust/demoblaze-browse-cart/demoblaze_locust.py \
  --headless -u 10 -r 2 -t 1m
```

Through Taurus, which is how BlazeMeter runs it:

```bash
bzt Locust/demoblaze-browse-cart/demoblaze-browse-cart.yml
```

## Web vitals for Playwright tests

To capture web vitals from a Playwright test on BlazeMeter and build the HTML
vitals dashboard, follow
[`.claude/skills/playwright-web-vitals/SKILL.md`](.claude/skills/playwright-web-vitals/SKILL.md).
The working example is `Playwright/dbankdemo-webvitals/`.

## Test accounts

Demo apps use public test accounts. They are not secrets, so they are committed
and uploaded to BlazeMeter with the script.

| App | URL | Credentials file |
| --- | --- | --- |
| Digital Bank | `http://dbankdemo.com/bank/login` | [`Playwright/dbankdemo-webvitals/credentials.ts`](Playwright/dbankdemo-webvitals/credentials.ts) |

Import the credentials file in a new script instead of typing the values again.
Put a real account only in an ignored file (`.env`, `auth.json`) or a CI secret.

## Conventions

- Keep each example self-contained. Put data files next to the script that reads
  them.
- Keep a Taurus YAML beside the script, because the example is meant for
  BlazeMeter.
- Do not commit `.venv/`, test artifacts, reports, or `target/` and `results/`
  build output. The root `.gitignore` covers these.
- Do not commit credentials. `.env`, `auth.json` and `api-key*.json` are local
  only. In a JMeter script, read a credential from a BlazeMeter secret with
  `${__P(BZM_SECRET_<name>)}`. See `JMeter/blazemeter-secrets/`.
- Demo tests point at public demo sites, such as `demoblaze.com` and
  `blazedemo.com`. Do not point an example at a customer or production system.
