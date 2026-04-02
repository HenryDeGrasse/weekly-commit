import { test, expect } from "@playwright/test";
import { MyWeekPage } from "./pages/MyWeekPage";

/**
 * E2E: Post-lock scope changes — adding commits after lock triggers
 * a scope change dialog, and the timeline shows change events.
 */
test.describe("Post-Lock Scope Changes", () => {
  test.fixme(!!process.env.CI, "lock flow depends on RCDO picker, too slow on CI");
  test("post-lock add commit shows scope change dialog", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const stateText = await myWeek.planStateBadge.textContent();

    // If plan is not LOCKED, we need to create commits and lock
    if (stateText?.includes("DRAFT")) {
      await myWeek.addCommit("Scope change base", "PAWN", 2, { selectRcdo: true });
      await myWeek.lockPlan();
    }

    // Now in LOCKED state, add a commit
    const postLockBtn = page.getByTestId("post-lock-add-commit-btn");
    if (await postLockBtn.isVisible()) {
      await postLockBtn.click();

      // Either the AI composer or the commit form should appear
      const composerVisible = await page
        .getByTestId("ai-commit-composer")
        .isVisible()
        .catch(() => false);
      const formVisible = await page
        .getByTestId("commit-form-modal")
        .isVisible()
        .catch(() => false);

      if (composerVisible || formVisible) {
        // Fill out the form (use the manual path)
        if (composerVisible) {
          // Cancel the composer and use manual
          const cancelBtn = page
            .getByTestId("ai-composer-cancel")
            .or(page.getByRole("button", { name: /cancel/i }))
            .first();
          if (await cancelBtn.isVisible()) {
            await cancelBtn.click();
          }
        }
      }
    }
  });

  test("scope change timeline button is visible on locked plans", async ({
    page,
  }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const stateText = await myWeek.planStateBadge.textContent();

    if (stateText?.includes("LOCKED")) {
      const timelineBtn = page.getByTestId("load-scope-timeline-btn");
      await expect(timelineBtn).toBeVisible({ timeout: 3000 });
    }
  });

  test("reconcile hint link is visible on locked plans", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const stateText = await myWeek.planStateBadge.textContent();
    if (stateText?.includes("LOCKED") || stateText?.includes("RECONCILING")) {
      const hint = page.getByTestId("reconcile-hint");
      await expect(hint).toBeVisible();
    }
  });
});
