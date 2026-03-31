import { test, expect } from "@playwright/test";
import { AdminPage } from "./pages/AdminPage";

/**
 * E2E: Admin page — org cadence config + team overrides.
 *
 * Most tests mock API routes to control data and test error states.
 * The admin page hits GET/PUT /api/config/org and GET/PUT /api/config/teams/{id}.
 *
 * Prerequisites:
 *   1. Backend running at http://localhost:8080 with dev seed data
 *   2. Frontend running at http://localhost:5173
 */

// ── Mock data ────────────────────────────────────────────────────────────

const MOCK_ORG_CONFIG = {
  id: "org-config-1",
  orgId: "org-1",
  weekStartDay: "MONDAY",
  draftOpenOffsetHours: -60,
  lockDueOffsetHours: 12,
  reconcileOpenOffsetHours: 89,
  reconcileDueOffsetHours: 106,
  defaultWeeklyBudget: 10,
  timezone: "America/New_York",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-03-01T00:00:00Z",
};

const MOCK_TEAM_CONFIG = {
  teamId: "team-1",
  orgId: "org-1",
  weekStartDay: "MONDAY",
  draftOpenOffsetHours: -60,
  lockDueOffsetHours: 12,
  reconcileOpenOffsetHours: 89,
  reconcileDueOffsetHours: 106,
  defaultWeeklyBudget: 10,
  timezone: "America/New_York",
  hasTeamOverride: false,
};

const MOCK_TEAM_CONFIG_WITH_OVERRIDE = {
  ...MOCK_TEAM_CONFIG,
  hasTeamOverride: true,
  defaultWeeklyBudget: 15,
  lockDueOffsetHours: 24,
};

function mockOrgConfigEndpoints(page: import("@playwright/test").Page) {
  void page.route("**/api/config/org", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(MOCK_ORG_CONFIG),
      });
    }
    if (route.request().method() === "PUT") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ ...MOCK_ORG_CONFIG, updatedAt: new Date().toISOString() }),
      });
    }
    return route.continue();
  });
}

function mockTeamConfigEndpoints(page: import("@playwright/test").Page) {
  void page.route("**/api/config/teams/**", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(MOCK_TEAM_CONFIG),
      });
    }
    if (route.request().method() === "PUT") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "team-override-1",
          teamId: "team-1",
          weekStartDay: null,
          draftOpenOffsetHours: null,
          lockDueOffsetHours: null,
          reconcileOpenOffsetHours: null,
          reconcileDueOffsetHours: null,
          defaultWeeklyBudget: null,
          timezone: null,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: new Date().toISOString(),
        }),
      });
    }
    return route.continue();
  });
}

function mockAllAdminEndpoints(page: import("@playwright/test").Page) {
  mockOrgConfigEndpoints(page);
  mockTeamConfigEndpoints(page);
}

// ── 1A: Org Config Section — Happy Path ─────────────────────────────────

test.describe("Admin Page — Org Config", () => {
  test("renders admin page with heading and settings icon", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.pageContainer).toBeVisible();
    await expect(admin.pageContainer).toContainText("Administration");
  });

  test("org config section loads and displays current values", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.orgConfigSection).toBeVisible();
    // Verify the week start day select has a value
    await expect(admin.orgWeekStartDay).toBeVisible();
    await expect(admin.orgWeekStartDay).toHaveValue("MONDAY");
  });

  test("week start day dropdown has all 7 days", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.orgWeekStartDay).toBeVisible();
    const options = admin.orgWeekStartDay.locator("option");
    await expect(options).toHaveCount(7);
  });

  test("timezone dropdown lists major timezones", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.orgTimezone).toBeVisible();
    await expect(admin.orgTimezone).toHaveValue("America/New_York");
    // Should have multiple timezone options
    const options = admin.orgTimezone.locator("option");
    const count = await options.count();
    expect(count).toBeGreaterThanOrEqual(5);
  });

  test("can change default weekly budget via number input", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.orgBudget).toBeVisible();
    await admin.orgBudget.fill("15");
    await expect(admin.orgBudget).toHaveValue("15");
  });

  test("cadence deadline fields are visible and functional", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    // Verify all 4 cadence day+time pairs are visible
    await expect(admin.orgDraftOffsetDay).toBeVisible();
    await expect(admin.orgDraftOffsetTime).toBeVisible();
    await expect(admin.orgLockOffsetDay).toBeVisible();
    await expect(admin.orgLockOffsetTime).toBeVisible();
    await expect(admin.orgReconcileOpenOffsetDay).toBeVisible();
    await expect(admin.orgReconcileOpenOffsetTime).toBeVisible();
    await expect(admin.orgReconcileDueOffsetDay).toBeVisible();
    await expect(admin.orgReconcileDueOffsetTime).toBeVisible();

    // The day selects should have 7 options each
    const lockDayOptions = admin.orgLockOffsetDay.locator("option");
    await expect(lockDayOptions).toHaveCount(7);
  });

  test("click Save Configuration shows Saved indicator", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.orgSaveBtn).toBeVisible();
    await admin.orgSaveBtn.click();

    // "Saved" indicator should appear
    await expect(admin.orgSavedIndicator).toBeVisible({ timeout: 5000 });
    await expect(admin.orgSavedIndicator).toContainText("Saved");
  });

  test("Saved indicator disappears after ~3 seconds", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await admin.orgSaveBtn.click();
    await expect(admin.orgSavedIndicator).toBeVisible({ timeout: 5000 });

    // Wait for it to disappear (3s timeout in the component + buffer)
    await expect(admin.orgSavedIndicator).toBeHidden({ timeout: 6000 });
  });

  test("mock GET /api/config/org returns 500 → error message displays", async ({ page }) => {
    void page.route("**/api/config/org", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Internal Server Error" }),
        });
      }
      return route.continue();
    });
    mockTeamConfigEndpoints(page);

    await page.goto("/weekly/admin");
    await page.getByTestId("admin-page").waitFor();

    // Error alert should be visible in the org config section
    const errorAlert = page.getByTestId("org-config-section").getByRole("alert");
    await expect(errorAlert).toBeVisible({ timeout: 5000 });
  });

  test("mock PUT /api/config/org returns 500 → error message, form retains values", async ({ page }) => {
    void page.route("**/api/config/org", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(MOCK_ORG_CONFIG),
        });
      }
      if (route.request().method() === "PUT") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Internal Server Error" }),
        });
      }
      return route.continue();
    });
    mockTeamConfigEndpoints(page);

    const admin = new AdminPage(page);
    await admin.goto();

    // Change budget to 20
    await admin.orgBudget.fill("20");
    await admin.orgSaveBtn.click();

    // Error should show
    const errorAlert = page.getByTestId("org-config-section").getByRole("alert");
    await expect(errorAlert).toBeVisible({ timeout: 5000 });

    // Form should retain the value we entered
    await expect(admin.orgBudget).toHaveValue("20");
  });
});

// ── 1B: Team Config Section ─────────────────────────────────────────────

test.describe("Admin Page — Team Config", () => {
  test("team config section is collapsed by default", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await expect(admin.teamConfigSection).toBeVisible();
    // The budget override field should NOT be visible until expanded
    await expect(admin.teamBudgetOverride).toBeHidden();
  });

  test("click header expands the section and shows effective values", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await admin.expandTeamConfig();

    // After expanding, we should see the config content
    // Wait for the team config to load
    await expect(admin.teamBudgetOverride).toBeVisible({ timeout: 5000 });
  });

  test("badge shows 'Using org defaults' when no team override exists", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();

    await admin.expandTeamConfig();

    // Should see "Using org defaults" badge
    await expect(admin.teamConfigSection).toContainText("Using org defaults", { timeout: 5000 });
  });

  test("badge shows 'Has team overrides' when override exists", async ({ page }) => {
    mockOrgConfigEndpoints(page);
    void page.route("**/api/config/teams/**", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(MOCK_TEAM_CONFIG_WITH_OVERRIDE),
        });
      }
      return route.continue();
    });

    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    await expect(admin.teamConfigSection).toContainText("Has team overrides", { timeout: 5000 });
  });

  test("can enter budget override value", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    await expect(admin.teamBudgetOverride).toBeVisible({ timeout: 5000 });
    await admin.teamBudgetOverride.fill("15");
    await expect(admin.teamBudgetOverride).toHaveValue("15");
  });

  test("can enter lock due override value", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    await expect(admin.teamLockOverride).toBeVisible({ timeout: 5000 });
    await admin.teamLockOverride.fill("24");
    await expect(admin.teamLockOverride).toHaveValue("24");
  });

  test("timezone override has 'Inherit from org' option", async ({ page }) => {
    mockAllAdminEndpoints(page);
    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    await expect(admin.teamTimezoneOverride).toBeVisible({ timeout: 5000 });
    // First option should be the inherit option (empty value)
    const firstOption = admin.teamTimezoneOverride.locator("option").first();
    await expect(firstOption).toContainText("Inherit from org");
  });

  test("click Save Overrides → success", async ({ page }) => {
    mockOrgConfigEndpoints(page);
    // Mock team GET then PUT
    let putCalled = false;
    void page.route("**/api/config/teams/**", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(MOCK_TEAM_CONFIG),
        });
      }
      if (route.request().method() === "PUT") {
        putCalled = true;
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "team-override-1",
            teamId: "team-1",
            weekStartDay: null,
            draftOpenOffsetHours: null,
            lockDueOffsetHours: null,
            reconcileOpenOffsetHours: null,
            reconcileDueOffsetHours: null,
            defaultWeeklyBudget: 15,
            timezone: null,
            createdAt: "2026-01-01T00:00:00Z",
            updatedAt: new Date().toISOString(),
          }),
        });
      }
      return route.continue();
    });

    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    await expect(admin.teamBudgetOverride).toBeVisible({ timeout: 5000 });
    await admin.teamBudgetOverride.fill("15");
    await admin.teamSaveBtn.click();

    // Verify the PUT was called (save succeeded without error)
    await expect.poll(() => putCalled, { timeout: 5000 }).toBe(true);
  });

  test("mock GET /api/config/teams/{id} returns 500 → error in expanded section", async ({ page }) => {
    mockOrgConfigEndpoints(page);
    void page.route("**/api/config/teams/**", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Internal Server Error" }),
        });
      }
      return route.continue();
    });

    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    // Error alert should appear
    const errorAlert = admin.teamConfigSection.getByRole("alert");
    await expect(errorAlert).toBeVisible({ timeout: 5000 });
  });

  test("mock PUT /api/config/teams/{id} returns 500 → error, form retains values", async ({ page }) => {
    mockOrgConfigEndpoints(page);
    void page.route("**/api/config/teams/**", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(MOCK_TEAM_CONFIG),
        });
      }
      if (route.request().method() === "PUT") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Internal Server Error" }),
        });
      }
      return route.continue();
    });

    const admin = new AdminPage(page);
    await admin.goto();
    await admin.expandTeamConfig();

    await expect(admin.teamBudgetOverride).toBeVisible({ timeout: 5000 });
    await admin.teamBudgetOverride.fill("20");
    await admin.teamSaveBtn.click();

    // Error alert should appear
    const errorAlert = admin.teamConfigSection.getByRole("alert");
    await expect(errorAlert).toBeVisible({ timeout: 5000 });

    // Form should retain the value
    await expect(admin.teamBudgetOverride).toHaveValue("20");
  });
});
