---
name: playwright-web-vitals
description: Capture web vitals (TTFB, FCP, LCP, CLS, INP) from a Playwright test on BlazeMeter and build the HTML vitals dashboard with the bzm-playwright-vitals and bzm-vitals-dashboard npm packages. Use when adding web vitals to a Playwright example, running one on BlazeMeter, generating a web vitals report for a master id, or wiring that flow into GitHub Actions.
---

# Playwright web vitals on BlazeMeter

The working template is [`Playwright/dbankdemo-webvitals/`](../../../Playwright/dbankdemo-webvitals/).
Copy its shape; read its files for the exact config. Package docs:
https://github.com/benjithompson/blazemeter-web-vitals-report

Two npm packages:

| Package | Role | Where it runs |
| --- | --- | --- |
| `bzm-playwright-vitals` | Collector. Records vitals for every navigation. | In the spec, locally and on the Engine. |
| `bzm-vitals-dashboard` | CLI. Turns a finished master into one `report.html`. | Locally or in CI, after the run. |

## Steps

1. **Collector.** Add `bzm-playwright-vitals` to `devDependencies` in the example's `package.json`. Change the spec import to `import { test, expect } from 'bzm-playwright-vitals';`. Nothing else in the test changes. Optional: `vitals.route('/order/{id}')` groups dynamic URLs under one row.
2. **Config.** `playwright.config.ts` must define a project named `chromium` (Taurus passes `--project=chromium`) and set `retries: 0` (retries add extra vitals records). The Taurus YAML uses `executor: playwright`, `browser: chromium`, and `modules.blazemeter.dedicated-ips: false`.
3. **Local check.** `npm install && npx playwright install chromium && npx playwright test`. Done when every test passes and `test-results/` holds `bzm-vitals-sample-*.json` files.
4. **BlazeMeter test.** Create the test, upload the YAML (main script), spec, any imported `.ts` files, `playwright.config.ts` and `package.json`. Leave out `package-lock.json` and `node_modules/`. A test created through the MCP gets a default 20-user JMeter override: set it with `configure_load` (`executor: playwright`, small `concurrency` and `iterations`) before you start it.
5. **Run.** Start the test. Done when the master's `ended` is not null.
6. **Dashboard.** Done when `report.html` exists:
   ```bash
   export BLAZEMETER_API_KEY=/path/to/api-key.json   # or BLAZEMETER_API_KEY_ID + BLAZEMETER_API_KEY_SECRET
   npx bzm-vitals-dashboard --master <masterId> --out web-vitals-report.html
   ```
   Without API access, download each Engine's `artifacts.zip` from the report's Logs tab and run `npx bzm-vitals-dashboard --artifacts <zip-or-folder> --out web-vitals-report.html`.
7. **CI.** Copy [`.github/workflows/dbankdemo-webvitals.yml`](../../../.github/workflows/dbankdemo-webvitals.yml) and the example's `run-on-blazemeter.mjs`. The workflow needs the repository secrets `BLAZEMETER_API_KEY_ID` and `BLAZEMETER_API_KEY_SECRET`. To run the workflow locally before a push, copy and run the example's `run-ci-local.sh` (it uses `act` in Docker; done when it prints `Workflow: PASS`, the report link and the dashboard path).

## Gotchas

- Wrap each user action in `test.step('<name>', ...)`. BlazeMeter reports each step as its own label with response times; without steps it reports one sample per whole test. Page-level vitals are only in the dashboard.
- INP needs a real interaction (typing, a click that renders). A journey that only navigates shows "no interaction". That is correct.
- Failed tests stay in the dashboard by default, because slow pages often cause the failure.
- The dashboard caches artifacts in `./.artifact-cache/`. `--refresh` fetches the master again. Both the cache and `web-vitals-report*.html` are git-ignored.
- `act` does not read Docker contexts. With Colima or Docker Desktop, set `DOCKER_HOST` to the context socket and pass `--container-daemon-socket -` (`run-ci-local.sh` does both).
- Never commit an API key file. `api-key*.json` is git-ignored.
