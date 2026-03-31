import { test, expect } from "@playwright/test";
import { TeamPage } from "./pages/TeamPage";

/**
 * E2E: Team Week dashboard — comprehensive tab and section testing.
 */
test.describe("Team Dashboard — Comprehensive", () => {
  test("team page loads with all section tabs", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await expect(teamPage.overviewTab).toBeVisible();
    await expect(teamPage.byPersonTab).toBeVisible();
    await expect(teamPage.exceptionsTab).toBeVisible();
  });

  test("overview section shows all 4 summary cards", async ({ page }) => {
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

  test("by-person tab shows team member data", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("by-person");
    const section = page.getByTestId("by-person-section");
    await expect(section).toBeVisible({ timeout: 5000 });

    const content = await section.textContent();
    expect(content).toBeTruthy();
    expect(content!.length).toBeGreaterThan(0);
  });

  test("chess distribution tab renders", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("chess");
    const panel = page.getByTestId("panel-chess");
    await expect(panel).toBeVisible({ timeout: 5000 });
  });

  test("exceptions tab shows exception data", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");
    const panel = page.getByTestId("panel-exceptions");
    await expect(panel).toBeVisible({ timeout: 5000 });
  });

  test("history tab renders", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("history");
    const panel = page.getByTestId("panel-history");
    await expect(panel).toBeVisible({ timeout: 5000 });
  });

  test("team week selector navigates between weeks", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    const initialLabel = await teamPage.weekLabel.textContent();
    await page.getByTestId("team-prev-week-btn").click();

    // Wait for week label to change
    await expect(teamPage.weekLabel).not.toHaveText(initialLabel!, { timeout: 5000 });
    const newLabel = await teamPage.weekLabel.textContent();
    expect(newLabel).not.toEqual(initialLabel);
  });

  test("team page breadcrumb shows Team Week", async ({ page }) => {
    await page.goto("/weekly/team");
    await page.getByTestId("page-team-week").waitFor({ timeout: 5000 });

    const breadcrumb = page.getByRole("navigation", { name: "Breadcrumb" });
    if (await breadcrumb.isVisible()) {
      await expect(breadcrumb).toContainText("Team");
    }
  });

  test("switching tabs rapidly does not crash", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    // Rapid tab switching
    await teamPage.switchToTab("by-person");
    await teamPage.switchToTab("chess");
    await teamPage.switchToTab("exceptions");
    await teamPage.switchToTab("history");
    await teamPage.switchToTab("overview");

    // Page should still be stable
    await expect(page.getByTestId("page-team-week")).toBeVisible();
  });
});
