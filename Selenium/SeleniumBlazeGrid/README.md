# BlazeMeter Selenium Grid Smoke Test

This repository contains a minimal Python-based Selenium smoke test that runs against the [BlazeMeter Selenium Grid](https://guide.blazemeter.com/hc/en-us/articles/207727209-BlazeMeter-Selenium-Grid). It opens the demo flight booking site [BlazeDemo](https://blazedemo.com) and verifies that the homepage loads successfully.

## Prerequisites

- Python 3.9 or newer
- BlazeMeter account with access to the Selenium grid (username and access key)

## Setup

1. Clone or download this repository.
2. Create and activate a virtual environment (recommended).

   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   ```

   *(On Windows PowerShell use `python -m venv .venv` followed by `.venv\\Scripts\\Activate.ps1`.)*
3. Install dependencies:

   ```bash
   pip install -r requirements.txt
   ```
4. Copy the provided environment template and add your credentials:

   ```bash
   cp .env.example .env
   ```

   Update the new `.env` file with:

   - `BLAZEMETER_KEY`: your BlazeMeter API key ID
   - `BLAZEMETER_SECRET`: the matching API secret
   - `BLAZEMETER_BASE`: optional. Hostname that defaults to `a.blazemeter.com`
   - `BLAZEMETER_GRID_URL`: optional. Provide a full grid URL without credentials if you need to override the default format

## Running the smoke test

Execute the test script directly:

```bash
python tests/test_blazemeter_grid.py
```

The script will:

1. Establish a Remote WebDriver session on the BlazeMeter grid using Chrome on Windows 11 (latest version).
2. Navigate to `https://blazedemo.com`.
3. Wait for the flight search button to appear and validate the page title.
4. Tear down the session.

On success, you should see the message:

```
BlazeMeter smoke test completed successfully
```

## Customising capabilities

The test uses Chrome by default. Adjust the capabilities inside `tests/test_blazemeter_grid.py` if you want to exercise different browsers or platforms:

```python
options.set_capability("browserVersion", "latest")
options.set_capability("platformName", "Windows 11")
```

Refer to the BlazeMeter documentation for the list of supported browsers, versions, and additional capability keys.

## Troubleshooting

- **Missing credentials**: The script raises a clear error if the required environment variables are not set.
- **Authentication failures**: Confirm that your user/access key combination is valid and has Selenium grid permissions.
- **Timeouts**: Increase `TIMEOUT_SECONDS` in the test script if the grid sessions or page load slowly.
- **Network restrictions**: Ensure outbound HTTPS traffic to `a.blazemeter.com` is allowed from your environment.

## Next steps

- Integrate this smoke test into your CI pipeline using the same environment variables.
- Expand the test to cover additional user flows or assertions.
