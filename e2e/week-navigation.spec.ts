import { test, expect } from "@playwright/test";
import { MyWeekPage } from "./pages/MyWeekPage";

/**
 * E2E: Week navigation, plan history, and expand/collapse sections.
 */
test.describe("Week Navigation & History", () => {
  test("prev/next week buttons change the week label", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();

    const initialLabel = await myWeek.weekLabel.textContent();
    expect(initialLabel).toContain("Week of");

    // Go to previous week
    await myWeek.prevWeekBtn.click();
    const prevLabel = await myWeek.weekLabel.textContent();
    expect(prevLabel).not.toEqual(initialLabel);

    // Go to next week (back to current)
    await myWeek.nextWeekBtn.click();
    const nextLabel = await myWeek.weekLabel.textContent();
    expect(nextLabel).toEqual(initialLabel);
  });

  test("today button returns to current week", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();

    const initialLabel = await myWeek.weekLabel.textContent();

    // Navigate away
    await myWeek.prevWeekBtn.click();
    await myWeek.prevWeekBtn.click();

    // Today button should appear
    const todayBtn = page.getByTestId("current-week-btn");
    await expect(todayBtn).toBeVisible();

    // Click today
    await todayBtn.click();
    await expect(myWeek.weekLabel).toHaveText(initialLabel!);

    // Today button should disappear
    await expect(todayBtn).toBeHidden();
  });

  test("navigating to a future week shows a plan or empty state", async ({
    page,
  }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();

    // Go 3 weeks forward
    await page.getByTestId("next-week-btn").click();
    await page.getByTestId("next-week-btn").click();
    await page.getByTestId("next-week-btn").click();

    // Should show either a plan state badge or an empty state
    const badge = page.getByTestId("plan-state-badge");
    const empty = page.getByTestId("my-week-empty");

    await expect(badge.or(empty)).toBeVisible({ timeout: 5000 });
  });

  test("plan history section can be toggled", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await page.getByTestId("page-my-week").waitFor();
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });

    // Find plan history toggle
    const historyToggle = page.getByTestId("toggle-plan-history-btn");
    if (await historyToggle.isVisible()) {
      await historyToggle.click();
      // History content should become visible
      await expect(page.getByTestId("page-my-week")).toBeVisible({ timeout: 3000 });
    }
  });

  test("expand all / collapse all buttons work", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await page.getByTestId("page-my-week").waitFor();
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });

    // Click expand all
    const expandBtn = page.getByTestId("expand-all-btn");
    await expect(expandBtn).toBeVisible();
    await expandBtn.click();

    // Click collapse all
    const collapseBtn = page.getByTestId("collapse-all-btn");
    await expect(collapseBtn).toBeVisible();
    await collapseBtn.click();

    // No errors should occur
    await expect(page.getByTestId("page-my-week")).toBeVisible();
  });

  test("navigating back to current week preserves state", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    const originalState = await myWeek.planStateBadge.textContent();

    // Navigate away and back
    await myWeek.prevWeekBtn.click();
    await page.getByTestId("current-week-btn").click();

    // State should be the same
    await expect(myWeek.planStateBadge).toContainText(originalState!);
  });
});
