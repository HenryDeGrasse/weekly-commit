import { type Page, type Locator, expect } from "@playwright/test";

/**
 * Page Object Model for the My Week page.
 */
export class MyWeekPage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly weekLabel: Locator;
  readonly prevWeekBtn: Locator;
  readonly nextWeekBtn: Locator;
  readonly addCommitBtn: Locator;
  readonly lockPlanBtn: Locator;
  readonly planStateBadge: Locator;
  readonly capacityMeter: Locator;
  readonly commitList: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("page-my-week");
    this.weekLabel = page.getByTestId("week-label");
    this.prevWeekBtn = page.getByTestId("prev-week-btn");
    this.nextWeekBtn = page.getByTestId("next-week-btn");
    this.addCommitBtn = page.getByTestId("add-commit-btn");
    this.lockPlanBtn = page.getByTestId("lock-plan-btn");
    this.planStateBadge = page.getByTestId("plan-state-badge");
    this.capacityMeter = page.getByTestId("capacity-meter");
    this.commitList = page.getByTestId("commit-list");
  }

  async goto() {
    await this.page.goto("/weekly/my-week");
    await this.pageContainer.waitFor();
  }

  async expectDraftState() {
    await expect(this.planStateBadge).toContainText("DRAFT");
    await expect(this.addCommitBtn).toBeVisible();
    await expect(this.lockPlanBtn).toBeVisible();
  }

  async addCommit(title: string, chessPiece: string, points?: number) {
    await this.addCommitBtn.click();
    await this.page.getByTestId("commit-form-modal").waitFor();

    await this.page.getByTestId("commit-form-title").fill(title);
    await this.page
      .getByTestId(`chess-piece-option-${chessPiece.toLowerCase()}`)
      .click();
    if (points) {
      await this.page.getByTestId(`estimate-btn-${points}`).click();
    }
    await this.page.getByTestId("commit-form-submit").click();
    // Wait for form to close
    await expect(this.page.getByTestId("commit-form-modal")).toBeHidden({
      timeout: 5000,
    });
  }

  async lockPlan() {
    await this.lockPlanBtn.click();
    // Pre-lock validation panel
    await this.page.getByTestId("pre-lock-validation-section").waitFor();
    const continueBtn = this.page.getByTestId("pre-lock-continue-btn");
    if (await continueBtn.isVisible()) {
      await continueBtn.click();
    }
    // Lock confirm dialog
    await this.page.getByTestId("lock-confirm-dialog").waitFor();
    await this.page.getByTestId("lock-confirm-btn").click();
    // Wait for state to change
    await expect(this.planStateBadge).toContainText("LOCKED", {
      timeout: 5000,
    });
  }
}
