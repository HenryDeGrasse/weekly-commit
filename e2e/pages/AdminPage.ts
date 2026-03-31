import { type Page, type Locator, expect } from "@playwright/test";

/**
 * Page Object Model for the Admin page.
 */
export class AdminPage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly orgConfigSection: Locator;
  readonly teamConfigSection: Locator;

  // Org config form fields
  readonly orgWeekStartDay: Locator;
  readonly orgTimezone: Locator;
  readonly orgBudget: Locator;
  readonly orgSaveBtn: Locator;
  readonly orgSavedIndicator: Locator;

  // Org cadence day+time fields
  readonly orgDraftOffsetDay: Locator;
  readonly orgDraftOffsetTime: Locator;
  readonly orgLockOffsetDay: Locator;
  readonly orgLockOffsetTime: Locator;
  readonly orgReconcileOpenOffsetDay: Locator;
  readonly orgReconcileOpenOffsetTime: Locator;
  readonly orgReconcileDueOffsetDay: Locator;
  readonly orgReconcileDueOffsetTime: Locator;

  // Team config fields
  readonly teamBudgetOverride: Locator;
  readonly teamLockOverride: Locator;
  readonly teamTimezoneOverride: Locator;
  readonly teamSaveBtn: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("admin-page");
    this.orgConfigSection = page.getByTestId("org-config-section");
    this.teamConfigSection = page.getByTestId("team-config-section");

    this.orgWeekStartDay = page.getByTestId("org-week-start-day");
    this.orgTimezone = page.getByTestId("org-timezone");
    this.orgBudget = page.getByTestId("org-budget");
    this.orgSaveBtn = page.getByTestId("org-save-btn");
    this.orgSavedIndicator = page.getByTestId("org-saved-indicator");

    this.orgDraftOffsetDay = page.getByTestId("org-draft-offset-day");
    this.orgDraftOffsetTime = page.getByTestId("org-draft-offset-time");
    this.orgLockOffsetDay = page.getByTestId("org-lock-offset-day");
    this.orgLockOffsetTime = page.getByTestId("org-lock-offset-time");
    this.orgReconcileOpenOffsetDay = page.getByTestId("org-reconcile-open-offset-day");
    this.orgReconcileOpenOffsetTime = page.getByTestId("org-reconcile-open-offset-time");
    this.orgReconcileDueOffsetDay = page.getByTestId("org-reconcile-due-offset-day");
    this.orgReconcileDueOffsetTime = page.getByTestId("org-reconcile-due-offset-time");

    this.teamBudgetOverride = page.getByTestId("team-budget-override");
    this.teamLockOverride = page.getByTestId("team-lock-override");
    this.teamTimezoneOverride = page.getByTestId("team-timezone-override");
    this.teamSaveBtn = page.getByTestId("team-save-btn");
  }

  async goto() {
    await this.page.goto("/weekly/admin");
    await this.pageContainer.waitFor();
  }

  async expandTeamConfig() {
    // Click the team config header to expand
    await this.teamConfigSection.locator("button").first().click();
  }
}
