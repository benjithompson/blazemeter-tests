const { test, expect } = require('@playwright/test');

test('login and logout', async ({ page }) => {
  // Navigate to login page
  await page.goto('http://dbankdemo.com/bank/login');

  // Fill in login credentials
  await page.getByRole('textbox', { name: 'Enter User Name' }).fill('jsmith@demo.io');
  await page.getByRole('textbox', { name: 'Enter Password' }).fill('Demo123!');

  // Click sign in button
  await page.getByRole('button', { name: 'Sign in' }).click();

  // Verify login successful
  await expect(page).toHaveURL(/.*\/bank\/home/);

  // Click user avatar to open menu
  await page.getByRole('img', { name: 'User Avatar' }).click();

  // Click logout
  await page.getByRole('link', { name: 'Logout' }).click();

  // Verify logout successful
  await expect(page).toHaveURL(/.*\/bank\/login/);
  await expect(page.locator('text=Logout completed')).toBeVisible();
});
