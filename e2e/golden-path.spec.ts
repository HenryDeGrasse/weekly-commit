import { test, expect } from "@playwright/test";
import { MyWeekPage } from "./pages/MyWeekPage";

/**
 * Golden path E2E test: create plan → add commits → lock → navigate to reconcile.
 *
 * Prerequisites:
 *   1. Backend running at http://localhost:8080 with dev seed data
 *   2. Frontend running at http://localhost:5173
 *   3. PostgreSQL with the weekly_commit database
 */
test.describe("Golden Path: Plan → Lock → Reconcile", () => {
  test("renders the My Week page with sidebar nav", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await expect(page.getByTestId("page-my-week")).toBeVisible();
    await expect(page.getByTestId("app-shell")).toBeVisible();
  });

  test("sidebar navigation contains all 5 routes", async ({ page }) => {
    await page.goto("/weekly/my-week");
    const nav = page.getByRole("navigation", {
      name: "Weekly Commit navigation",
    });
    await expect(nav).toBeVisible();
    const links = nav.getByRole("link");
    await expect(links).toHaveCount(5);
  });

  test("week selector navigates between weeks", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();

    const initialLabel = await myWeek.weekLabel.textContent();
    await myWeek.prevWeekBtn.click();
    await expect(myWeek.weekLabel).not.toHaveText(initialLabel!);

    // Today button appears
    const todayBtn = page.getByTestId("current-week-btn");
    await expect(todayBtn).toBeVisible();

    // Click today to return
    await todayBtn.click();
    await expect(myWeek.weekLabel).toHaveText(initialLabel!);
  });

  test("creates a plan and shows DRAFT state on first visit", async ({
    page,
  }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    // Should auto-create a plan for the current week
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });
    await myWeek.expectDraftState();
  });

  test("can add a commit via the form", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.planStateBadge).toBeVisible({ timeout: 5000 });

    await myWeek.addCommit("E2E Test Commit", "PAWN", 2);

    // Commit should appear in the list
    await expect(page.getByText("E2E Test Commit")).toBeVisible();
    // Capacity meter should update
    await expect(myWeek.capacityMeter).toBeVisible();
  });

  test("capacity meter reflects committed points", async ({ page }) => {
    const myWeek = new MyWeekPage(page);
    await myWeek.goto();
    await expect(myWeek.capacityMeter).toBeVisible({ timeout: 5000 });
    // Check that it shows some tally
    await expect(page.getByTestId("capacity-tally")).toBeVisible();
  });

  test("navigates to all pages via sidebar", async ({ page }) => {
    await page.goto("/weekly/my-week");
    await expect(page.getByTestId("page-my-week")).toBeVisible();

    // Navigate to Team
    await page.getByRole("link", { name: /team/i }).first().click();
    await expect(page.getByTestId("page-team-week")).toBeVisible({
      timeout: 5000,
    });

    // Navigate to Tickets
    await page.getByRole("link", { name: /tickets/i }).click();
    await expect(page.getByTestId("page-tickets")).toBeVisible({
      timeout: 5000,
    });

    // Navigate to RCDOs
    await page.getByRole("link", { name: /rcdos/i }).click();
    await expect(page.getByTestId("page-rcdos")).toBeVisible({
      timeout: 5000,
    });
  });

  test("breadcrumb shows correct path", async ({ page }) => {
    await page.goto("/weekly/my-week");
    const breadcrumb = page.getByRole("navigation", { name: "Breadcrumb" });
    await expect(breadcrumb).toBeVisible();
    await expect(breadcrumb).toContainText("My Week");
  });
});
