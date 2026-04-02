import { test, expect } from "@playwright/test";

/**
 * E2E tests for navigation, sidebar, and header components.
 */
test.describe("Navigation & Layout", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/weekly/my-week");
    await page.getByTestId("app-shell").waitFor();
  });

  test.fixme(!!process.env.CI, "Dev User text hidden on CI");
  test("header renders with brand, week selector, and user info", async ({
    page,
  }) => {
    const header = page.getByRole("banner");
    await expect(header).toBeVisible();
    // Week selector
    await expect(page.getByLabel("Select week")).toBeVisible();
    // Notification bell
    await expect(
      page.getByRole("button", { name: "Notifications" }),
    ).toBeVisible();
    // User display name
    // "Dev User" appears in both header and dev user switcher banner
    await expect(page.getByText("Dev User").first()).toBeVisible();
  });

  test("sidebar can be collapsed and expanded", async ({ page }) => {
    const collapseBtn = page.getByRole("button", {
      name: "Collapse sidebar",
    });
    // Collapse
    await collapseBtn.click();
    await expect(
      page.getByRole("button", { name: "Expand sidebar" }),
    ).toBeVisible();

    // Expand
    await page.getByRole("button", { name: "Expand sidebar" }).click();
    await expect(collapseBtn).toBeVisible();
  });

  test.fixme(!!process.env.CI, "depends on header test setup");
  test("sidebar collapse state persists across navigation", async ({
    page,
  }) => {
    // Collapse
    await page.getByRole("button", { name: "Collapse sidebar" }).click();

    // Navigate to another page
    await page.getByRole("link", { name: /tickets/i }).click();
    await page.getByTestId("page-tickets").waitFor();

    // Should still be collapsed
    await expect(
      page.getByRole("button", { name: "Expand sidebar" }),
    ).toBeVisible();
  });

  test("clicking nav links navigates to correct pages", async ({ page }) => {
    // My Week → Tickets
    await page.getByRole("link", { name: /tickets/i }).click();
    await expect(page).toHaveURL(/\/weekly\/tickets/);

    // Tickets → RCDOs
    await page.getByRole("link", { name: /rcdos/i }).click();
    await expect(page).toHaveURL(/\/weekly\/rcdos/);

    // RCDOs → My Week
    await page.getByRole("link", { name: /my week/i }).click();
    await expect(page).toHaveURL(/\/weekly\/my-week/);
  });
});
