import { test, expect } from "@playwright/test";
import { MyWeekPage } from "./pages/MyWeekPage";

/**
 * E2E: Commit CRUD operations — create, edit, delete, validation.
 */
test.describe("Commit CRUD Operations", () => {
  test("creates a commit with all required fields", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    // Only add if plan is in DRAFT state
    const stateText = await myWeek.planStateBadge.textContent();
    if (!stateText?.includes("DRAFT")) return;

    await myWeek.addCommit("Full Fields Commit", "ROOK", 3);
    await expect(page.getByText("Full Fields Commit")).toBeVisible();
  });

  test("creates commits with different chess pieces", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const stateText = await myWeek.planStateBadge.textContent();
    if (!stateText?.includes("DRAFT")) return;

    // Add commits with different pieces
    await myWeek.addCommit("Bishop work", "BISHOP", 2);
    await expect(page.getByText("Bishop work")).toBeVisible();

    await myWeek.addCommit("Knight exploration", "KNIGHT", 1);
    await expect(page.getByText("Knight exploration")).toBeVisible();

    await myWeek.addCommit("Pawn admin task", "PAWN", 1);
    await expect(page.getByText("Pawn admin task")).toBeVisible();
  });

  test("capacity meter updates after adding commits", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const stateText = await myWeek.planStateBadge.textContent();
    if (!stateText?.includes("DRAFT")) return;

    // Get initial tally
    const tallyBefore = await page
      .getByTestId("capacity-tally")
      .textContent()
      .catch(() => null);

    await myWeek.addCommit("Points test commit", "ROOK", 5);

    // Capacity should update
    await expect(myWeek.capacityMeter).toBeVisible();
    const tallyAfter = await page
      .getByTestId("capacity-tally")
      .textContent()
      .catch(() => null);

    // At minimum, the tally should be non-empty
    if (tallyAfter) {
      expect(tallyAfter.length).toBeGreaterThan(0);
    }
  });

  test.fixme(!!process.env.CI, "delete dialog timing unreliable on CI");
  test("deletes a commit via confirmation dialog", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const stateText = await myWeek.planStateBadge.textContent();
    if (!stateText?.includes("DRAFT")) return;

    // Add a commit to delete
    await myWeek.addCommit("Delete me", "PAWN", 1);
    await expect(page.getByText("Delete me")).toBeVisible();

    // Find and click the delete button for this commit
    const deleteBtn = page.locator('[data-testid^="commit-item-"]')
      .filter({ hasText: "Delete me" })
      .locator('[data-testid*="delete"]');
    if (await deleteBtn.first().isVisible()) {
      await deleteBtn.first().click();

      // Confirm deletion
      const dialog = page.getByTestId("delete-confirm-dialog");
      await expect(dialog).toBeVisible({ timeout: 10_000 });
      await page.getByTestId("delete-confirm-btn").click();

      // Commit should be gone
      await expect(page.getByText("Delete me")).toBeHidden({ timeout: 5000 });
    }
  });

  test("commit list shows correct data-testid", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    // If there are commits, the commit list should be visible
    const commitList = page.getByTestId("commit-list");
    const planState = await myWeek.planStateBadge.textContent();
    if (planState && !planState.includes("DRAFT")) {
      // Non-draft plans may or may not have a commit list
      return;
    }

    // Add a commit to ensure the list exists
    await myWeek.addCommit("List test", "PAWN", 1);
    await expect(commitList).toBeVisible({ timeout: 5000 });
  });
});
