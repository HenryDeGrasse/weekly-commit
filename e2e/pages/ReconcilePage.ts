import { type Page, type Locator, expect } from "@playwright/test";

/**
 * Page Object Model for the Reconcile page.
 */
export class ReconcilePage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly planState: Locator;
  readonly commitList: Locator;
  readonly submitBtn: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("page-reconcile");
    this.planState = page.getByTestId("reconcile-plan-state");
    this.commitList = page.getByTestId("reconcile-commit-list");
    this.submitBtn = page.getByTestId("reconcile-submit-btn");
  }

  async goto(planId: string) {
    await this.page.goto(`/weekly/reconcile/${planId}`);
    await this.pageContainer.waitFor();
  }

  async openReconciliation() {
    const openBtn = this.page.getByTestId("open-reconciliation-btn");
    if (await openBtn.isVisible()) {
      await openBtn.click();
      await this.commitList.waitFor({ timeout: 5000 });
    }
  }

  async setOutcome(commitId: string, outcome: string) {
    const selector = this.page.getByTestId(
      `outcome-option-${commitId}-${outcome.toLowerCase()}`,
    );
    await selector.click();
  }

  async submitReconciliation() {
    await this.submitBtn.click();
    await this.page.getByTestId("reconcile-submit-dialog").waitFor();
    await this.page.getByTestId("reconcile-submit-confirm").click();
    await expect(this.planState).toContainText("RECONCILED", {
      timeout: 5000,
    });
  }
}
