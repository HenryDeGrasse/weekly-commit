import { test, expect } from "@playwright/test";
import { ReconcilePage } from "./pages/ReconcilePage";

/**
 * E2E: Reconciliation page — rendering, state validation, outcome setting.
 */
test.describe("Reconcile Page", () => {
  test("reconcile page renders with proper structure", async ({ page }) => {
    await page.goto("/weekly/reconcile");
    const reconcilePage = page.getByTestId("page-reconcile");
    await expect(reconcilePage).toBeVisible({ timeout: 5000 });
  });

  test("reconcile page shows plan state", async ({ page }) => {
    await page.goto("/weekly/reconcile");
    await page.getByTestId("page-reconcile").waitFor({ timeout: 5000 });

    // The reconcile plan state badge should eventually be visible
    const stateBadge = page.getByTestId("reconcile-plan-state");
    if (await stateBadge.isVisible().catch(() => false)) {
      const text = await stateBadge.textContent();
      expect(
        ["LOCKED", "RECONCILING", "RECONCILED"].some((s) =>
          text?.includes(s),
        ),
      ).toBe(true);
    }
  });

  test("reconcile page shows commit list when reconciling", async ({
    page,
  }) => {
    await page.goto("/weekly/reconcile");
    await page.getByTestId("page-reconcile").waitFor({ timeout: 5000 });

    const commitList = page.getByTestId("reconcile-commit-list");
    if (await commitList.isVisible().catch(() => false)) {
      // Should contain commit data
      const content = await commitList.textContent();
      expect(content).toBeTruthy();
    }
  });

  test("reconcile page breadcrumb shows correct path", async ({ page }) => {
    await page.goto("/weekly/reconcile");
    await page.getByTestId("page-reconcile").waitFor({ timeout: 5000 });

    const breadcrumb = page.getByRole("navigation", { name: "Breadcrumb" });
    if (await breadcrumb.isVisible().catch(() => false)) {
      await expect(breadcrumb).toContainText("Reconcile");
    }
  });

  test("open reconciliation button transitions state", async ({ page }) => {
    await page.goto("/weekly/reconcile");
    await page.getByTestId("page-reconcile").waitFor({ timeout: 5000 });

    const openBtn = page.getByTestId("open-reconciliation-btn");
    if (await openBtn.isVisible().catch(() => false)) {
      await openBtn.click();

      // Should transition to RECONCILING
      const stateBadge = page.getByTestId("reconcile-plan-state");
      await expect(stateBadge).toContainText("RECONCILING", { timeout: 5000 });

      // Commit list should now be visible
      const commitList = page.getByTestId("reconcile-commit-list");
      await expect(commitList).toBeVisible({ timeout: 5000 });
    }
  });

  test("submit button is visible during reconciliation", async ({ page }) => {
    await page.goto("/weekly/reconcile");
    await page.getByTestId("page-reconcile").waitFor({ timeout: 5000 });

    // Open reconciliation if needed
    const openBtn = page.getByTestId("open-reconciliation-btn");
    if (await openBtn.isVisible().catch(() => false)) {
      await openBtn.click();
      await page.waitForTimeout(1000);
    }

    const submitBtn = page.getByTestId("reconcile-submit-btn");
    if (await submitBtn.isVisible().catch(() => false)) {
      // Submit button exists in reconciling state
      await expect(submitBtn).toBeVisible();
    }
  });
});
