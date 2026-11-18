# dbankdemo-webvitals

This example runs a Playwright login journey against the Digital Bank demo app
(`http://dbankdemo.com`) on BlazeMeter. It captures web vitals (TTFB, FCP, LCP,
CLS, INP) for each page with
[`bzm-playwright-vitals`](https://github.com/benjithompson/blazemeter-web-vitals-report),
and builds one self-contained HTML dashboard with `bzm-vitals-dashboard`.

| File | Purpose |
| --- | --- |
| [dbank-login.spec.ts](dbank-login.spec.ts) | The journey: login, dashboard, View Checking, logout. Imports `test` from `bzm-playwright-vitals`. |
| [credentials.ts](credentials.ts) | The Digital Bank test account. Public demo data, not a secret. |
| [playwright.config.ts](playwright.config.ts) | Defines the `chromium` project that Taurus selects. `retries: 0`. |
| [config.yml](config.yml) | Taurus config and BlazeMeter main script: 2 users, 3 iterations, `us-east4-a`. |
| [package.json](package.json) | Dependencies for the local run and for the Engine's `npm install`. |
| [run-on-blazemeter.mjs](run-on-blazemeter.mjs) | Uploads the files, starts the BlazeMeter test, waits for the end. Used by CI. |

The BlazeMeter test is `dbankdemo-webvitals` (test id `16072257`) in the
project "Playwright Tests" (project id `2601165`).

## Run locally

```bash
cd Playwright/dbankdemo-webvitals
npm install
npx playwright install chromium
npx playwright test
```

The collector writes `bzm-vitals-*.json` files to `test-results/`.

## Run on BlazeMeter and build the dashboard

```bash
export BLAZEMETER_API_KEY=/path/to/api-key.json   # never commit this file
node run-on-blazemeter.mjs                        # prints the master id
npx bzm-vitals-dashboard --master <masterId> --out web-vitals-report.html
```

## Run the CI workflow locally

```bash
brew install act                                   # once; needs a running Docker engine
Playwright/dbankdemo-webvitals/run-ci-local.sh     # uses api-key.json in the repo root
```

The script runs the GitHub Actions workflow in Docker with `act`. At the end it
prints the job status (PASS or FAIL), the BlazeMeter report link, and the path
of `web-vitals-report-<masterId>.html` in this folder.

## CI

The workflow [dbankdemo-webvitals.yml](../../.github/workflows/dbankdemo-webvitals.yml)
runs the local smoke test, then the BlazeMeter run, then uploads
`web-vitals-report.html` as a build artifact. It needs the repository secrets
`BLAZEMETER_API_KEY_ID` and `BLAZEMETER_API_KEY_SECRET`.
