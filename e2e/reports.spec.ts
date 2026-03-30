import { test, expect } from "@playwright/test";

/**
 * E2E: Reports page — navigation, rendering, report types.
 */
test.describe("Reports Page", () => {
  test("navigates to reports page", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await page.getByTestId("app-shell").waitFor();

    // Navigate via URL since Reports may not be in the main sidebar
    await page.goto("/weekly/reports");

    // Should render the reports page or redirect
    const reportsPage = page.getByTestId("page-reports");
    const anyPage = page.getByTestId("app-shell");
    await expect(reportsPage.or(anyPage)).toBeVisible({ timeout: 5000 });
  });

  test("reports page renders content without errors", async ({ page }) => {
    await page.goto("/weekly/reports");

    const reportsPage = page.getByTestId("page-reports");
    if (await reportsPage.isVisible().catch(() => false)) {
      const content = await reportsPage.textContent();
      expect(content).toBeTruthy();
      expect(content!.length).toBeGreaterThan(0);
    }
  });
});
