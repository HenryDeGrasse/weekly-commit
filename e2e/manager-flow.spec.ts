import { test, expect } from "@playwright/test";
import { TeamPage } from "./pages/TeamPage";

/**
 * E2E tests for the manager flow: team view → exception queue → comment.
 */
test.describe("Manager Flow: Team Dashboard", () => {
  test("team page loads and shows tabs", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    // All tabs should be present
    await expect(teamPage.overviewTab).toBeVisible();
    await expect(teamPage.byPersonTab).toBeVisible();
    await expect(teamPage.exceptionsTab).toBeVisible();
  });

  test("can switch between tabs", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    // Switch to By Person
    await teamPage.switchToTab("by-person");
    await expect(page.getByTestId("panel-by-person")).toBeVisible();
    await expect(page.getByTestId("by-person-section")).toBeVisible();

    // Switch to Chess
    await teamPage.switchToTab("chess");
    await expect(page.getByTestId("panel-chess")).toBeVisible();

    // Switch to Exceptions
    await teamPage.switchToTab("exceptions");
    await expect(page.getByTestId("panel-exceptions")).toBeVisible();

    // Switch to History
    await teamPage.switchToTab("history");
    await expect(page.getByTestId("panel-history")).toBeVisible();
  });

  test("overview section shows summary cards", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await expect(page.getByTestId("overview-section")).toBeVisible({
      timeout: 5000,
    });
    await expect(page.getByTestId("lock-compliance-card")).toBeVisible();
    await expect(page.getByTestId("reconcile-compliance-card")).toBeVisible();
    await expect(page.getByTestId("points-summary-card")).toBeVisible();
    await expect(page.getByTestId("exceptions-overview-card")).toBeVisible();
  });

  test("week selector works on team page", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    const initialLabel = await teamPage.weekLabel.textContent();
    await page.getByTestId("team-prev-week-btn").click();
    await expect(teamPage.weekLabel).not.toHaveText(initialLabel!);
  });
});
