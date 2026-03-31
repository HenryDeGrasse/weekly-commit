import { type Page, type Locator } from "@playwright/test";

/**
 * Page Object Model for the Reports page.
 */
export class ReportsPage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly reportsEmpty: Locator;
  readonly velocityChart: Locator;
  readonly pvaChart: Locator;
  readonly achievementRateChart: Locator;
  readonly chessDistChart: Locator;
  readonly scopeChangeChart: Locator;
  readonly cfChart: Locator;
  readonly complianceChart: Locator;
  readonly exceptionAgingTable: Locator;
  readonly aiAcceptanceCard: Locator;
  readonly chartSkeleton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("reports-page");
    this.reportsEmpty = page.getByTestId("reports-empty");
    this.velocityChart = page.getByTestId("velocity-chart");
    this.pvaChart = page.getByTestId("pva-chart");
    this.achievementRateChart = page.getByTestId("achievement-rate-chart");
    this.chessDistChart = page.getByTestId("chess-dist-chart");
    this.scopeChangeChart = page.getByTestId("scope-change-chart");
    this.cfChart = page.getByTestId("cf-chart");
    this.complianceChart = page.getByTestId("compliance-chart");
    this.exceptionAgingTable = page.getByTestId("exception-aging-table");
    this.aiAcceptanceCard = page.getByTestId("ai-acceptance-card");
    this.chartSkeleton = page.getByTestId("report-chart-skeleton");
  }

  async goto() {
    await this.page.goto("/weekly/reports");
    await this.pageContainer.waitFor();
  }
}
