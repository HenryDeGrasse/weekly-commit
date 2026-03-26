import { type Page, type Locator, expect } from "@playwright/test";

/**
 * Page Object Model for the Team Week page.
 */
export class TeamPage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly weekLabel: Locator;
  readonly overviewTab: Locator;
  readonly byPersonTab: Locator;
  readonly exceptionsTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("page-team-week");
    this.weekLabel = page.getByTestId("team-week-label");
    this.overviewTab = page.getByTestId("tab-overview");
    this.byPersonTab = page.getByTestId("tab-by-person");
    this.exceptionsTab = page.getByTestId("tab-exceptions");
  }

  async goto(teamId?: string) {
    const path = teamId ? `/weekly/team/${teamId}` : "/weekly/team";
    await this.page.goto(path);
    await this.pageContainer.waitFor();
  }

  async switchToTab(
    tab: "overview" | "by-person" | "by-rcdo" | "chess" | "uncommitted" | "exceptions" | "history",
  ) {
    await this.page.getByTestId(`tab-${tab}`).click();
    await this.page.getByTestId(`panel-${tab}`).waitFor();
  }
}
