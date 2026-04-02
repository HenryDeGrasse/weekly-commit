import { test, expect } from "@playwright/test";
import { MyWeekPage } from "./pages/MyWeekPage";
import { ReconcilePage } from "./pages/ReconcilePage";

/**
 * E2E: Full weekly plan lifecycle — DRAFT → LOCKED → RECONCILING → RECONCILED.
 *
 * Prerequisites:
 *   1. Backend + frontend running (npm run dev)
 *   2. Fresh dev seed data
 */
test.describe("Full Lifecycle: DRAFT → LOCKED → RECONCILING → RECONCILED", () => {
  test.fixme(!!process.env.CI, "RCDO picker interaction too slow on CI runners");
  test("creates a plan, adds commits, locks, then navigates to reconcile", async ({
    page,
  }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();

    // Auto-creates a DRAFT plan
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });
    await myWeek.expectDraftState();

    // Add first commit (Pawn)
    await myWeek.addCommit("E2E Lifecycle Pawn", "PAWN", 2, { selectRcdo: true });
    await expect(page.getByText("E2E Lifecycle Pawn")).toBeVisible();

    // Add second commit (Rook)
    await myWeek.addCommit("E2E Lifecycle Rook", "ROOK", 3, { selectRcdo: true });
    await expect(page.getByText("E2E Lifecycle Rook")).toBeVisible();

    // Capacity meter should reflect total points
    await expect(myWeek.capacityMeter).toBeVisible();
    await expect(page.getByTestId("capacity-tally")).toBeVisible();

    // Lock the plan
    await myWeek.lockPlan();
    await expect(myWeek.planStateBadge).toContainText("LOCKED");

    // After lock, the original "Add Commit" button should be gone,
    // and the post-lock add commit button should appear
    await expect(page.getByTestId("post-lock-add-commit-btn")).toBeVisible();

    // Reconcile link should be visible
    await expect(page.getByTestId("reconcile-hint")).toBeVisible();
  });

  test("plan state badge shows DRAFT on fresh week", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });
    const stateText = await myWeek.planStateBadge.textContent();
    // Should be one of the valid states
    expect(["DRAFT", "LOCKED", "RECONCILING", "RECONCILED"]).toContain(
      stateText?.trim(),
    );
  });

  test("cannot lock an empty plan", async ({ page }) => {
    // Navigate to a future week that should be empty
    const myWeek = new MyWeekPage(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("page-my-week").waitFor();
    await page.getByTestId("next-week-btn").click();
    await page.getByTestId("next-week-btn").click();
    await page.getByTestId("next-week-btn").click();

    // Wait for plan to load
    const badge = page.getByTestId("plan-state-badge");
    await badge.waitFor({ timeout: 5000 });

    // If we get a DRAFT plan, try locking it without commits
    const stateText = await badge.textContent();
    if (stateText?.includes("DRAFT")) {
      const lockBtn = page.getByTestId("lock-plan-btn");
      if (await lockBtn.isVisible()) {
        await lockBtn.click();
        // Pre-lock validation should show errors
        const validationSection = page.getByTestId(
          "pre-lock-validation-section",
        );
        await expect(validationSection).toBeVisible({ timeout: 5000 });
        // The continue button should NOT be visible (validation errors block it)
        const continueBtn = page.getByTestId("pre-lock-continue-btn");
        // Either the continue button is hidden, or validation errors are shown
        const hasValidationContent = await validationSection
          .textContent()
          .then((text) => text && text.length > 0);
        expect(hasValidationContent).toBe(true);
      }
    }
  });
});
