"""Simple Selenium smoke test for BlazeMeter Selenium grid."""
from __future__ import annotations

import os
import sys
from contextlib import suppress
from typing import cast
from urllib.parse import urlsplit, urlunsplit

from dotenv import load_dotenv
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

TIMEOUT_SECONDS = 30
BLAZEDEMO_HOME = "https://blazedemo.com"

def _build_remote_url(key: str, secret: str, base_host: str) -> str:
    """Return a BlazeMeter grid URL embedding authentication credentials."""

    sanitized_base = base_host.replace("https://", "").replace("http://", "").strip("/")
    return f"https://{key}:{secret}@{sanitized_base}/api/v4/grid/wd/hub"

def create_driver() -> webdriver.Remote:
    """Create a Remote WebDriver session against the BlazeMeter grid."""
    load_dotenv()

    key = cast(str, os.getenv("BLAZEMETER_KEY"))
    secret = cast(str, os.getenv("BLAZEMETER_SECRET"))
    base_host = os.getenv("BLAZEMETER_BASE", "a.blazemeter.com")
    remote_url = _build_remote_url(key, secret, base_host)

    bzm_options = {
        "blazemeter.sessionName": "BlazeMeter Grid Smoke Test",
        "blazemeter.projectId": "2507934",
        "blazemeter.testName": "BlazeDemo Smoke",
        "blazemeter.videoEnabled": "True"
    }

    options = webdriver.ChromeOptions()
    options.set_capability("bzm:options", bzm_options)

    return webdriver.Remote(command_executor=remote_url, options=options)

def run_smoke_test() -> None:
    """Run a single-page smoke test against BlazeDemo."""
    driver = create_driver()
    test_context = {
        "testSuiteName": "Smoke Suite",
        "testCaseName": "Homepage loads",
    }
    try:
        driver.execute_script("/* FLOW_MARKER test-case-start */", test_context)
        driver.get(BLAZEDEMO_HOME)
        WebDriverWait(driver, TIMEOUT_SECONDS).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='submit']"))
        )
        assert "BlazeDemo" in driver.title
        driver.execute_script(
            "/* FLOW_MARKER test-case-stop */",
            {**test_context, "status": "passed", "message": "Homepage available"},
        )
    except Exception:
        driver.execute_script(
            "/* FLOW_MARKER test-case-stop */",
            {**test_context, "status": "failed", "message": "Smoke test failure"},
        )
        raise
    finally:
        with suppress(Exception):
            driver.quit()

def main() -> int:
    try:
        run_smoke_test()
    except Exception as exc:  # noqa: BLE001 - display friendly message
        print(f"Test failed: {exc}", file=sys.stderr)
        return 1
    print("BlazeMeter smoke test completed successfully")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
