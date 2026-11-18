// Digital Bank login journey with web vitals capture.
//
// The ONE collector-aware line is the import: 'bzm-playwright-vitals' wraps
// '@playwright/test' and records TTFB, FCP, LCP, CLS and INP for every page
// the test visits. The rest is a plain Playwright test.
//
// Journey (recorded with the Playwright MCP against dbankdemo.com):
//   1. /bank/login               - login page
//   2. /bank/home                - dashboard after sign in
//   3. /bank/account/checking-view - "View Checking" from the side menu
//   4. /bank/login?logout        - logout
import { test, expect } from 'bzm-playwright-vitals';
import type { Locator } from '@playwright/test';
import { dbank } from './credentials';

// The Digital Bank menus get their click handlers after page load. A click
// that comes too early does nothing, so retry until the menu item shows.
async function openMenu(trigger: Locator, item: Locator) {
  await expect(async () => {
    await trigger.click();
    await expect(item).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 20_000 });
}

test('Digital Bank login, view checking, logout', async ({ page }) => {
  await test.step('Open login page', async () => {
    await page.goto(`${dbank.baseURL}/bank/login`);
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  });

  await test.step('Sign in', async () => {
    // Typing and clicking are real interactions, so the login page gets an INP.
    await page.getByRole('textbox', { name: 'Enter User Name' }).fill(dbank.username);
    await page.getByRole('textbox', { name: 'Enter Password' }).fill(dbank.password);
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/bank\/home/);
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
  });

  await test.step('View checking accounts', async () => {
    // Expanding the side menu is a client-side interaction on the home page.
    const viewChecking = page.getByRole('link', { name: 'View Checking' });
    await openMenu(page.getByRole('link', { name: /^\W*Checking$/ }), viewChecking);
    await viewChecking.click();

    await expect(page).toHaveURL(/\/bank\/account\/checking-view/);
    await expect(page.getByRole('heading', { name: 'View Checking Accounts' })).toBeVisible();
  });

  await test.step('Log out', async () => {
    const logout = page.getByRole('link', { name: /Logout/ });
    await openMenu(page.getByRole('img', { name: 'User Avatar' }), logout);
    await logout.click();

    await expect(page).toHaveURL(/\/bank\/login\?logout/);
  });
});
