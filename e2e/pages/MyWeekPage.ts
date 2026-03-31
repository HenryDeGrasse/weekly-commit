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

  /**
   * Opens the commit form (bypassing AI Composer if enabled).
   * Returns when the commit-form-modal is visible.
   */
  async openCommitForm() {
    // Try the manual-add button first (bypasses AI Composer)
    const manualBtn = this.page.getByTestId("add-commit-manually-btn");
    if (await manualBtn.isVisible().catch(() => false)) {
      await manualBtn.click();
    } else {
      await this.addCommitBtn.click();
    }

    // If the AI Composer opened instead, switch to manual
    const aiComposer = this.page.getByTestId("ai-commit-composer");
    if (await aiComposer.isVisible().catch(() => false)) {
      const switchLink = this.page.getByTestId("ai-composer-switch-manual-link")
        .or(this.page.getByTestId("ai-composer-switch-manual-btn"));
      await switchLink.click();
    }

    await this.page.getByTestId("commit-form-modal").waitFor({ timeout: 5000 });
  }

  /**
   * Select the first available OUTCOME node in the RCDO picker.
   * Opens the picker, expands the tree, then clicks the first leaf OUTCOME node.
   */
  async selectRcdoInPicker() {
    const rcdoToggle = this.page.getByTestId("rcdo-picker-toggle");
    if (!(await rcdoToggle.isVisible().catch(() => false))) return;

    await rcdoToggle.click();
    const rcdoPanel = this.page.getByTestId("rcdo-picker-panel");
    await expect(rcdoPanel).toBeVisible({ timeout: 3000 });

    // Click "Expand all" button inside the picker to reveal Outcome nodes
    const expandAllBtn = rcdoPanel.getByRole("button", { name: "Expand all nodes" });
    if (await expandAllBtn.isVisible().catch(() => false)) {
      await expandAllBtn.click();
    }

    // Wait for tree nodes to appear after expansion
    const treeNodes = rcdoPanel.locator("[data-testid^='tree-node-']");
    await expect(treeNodes.first()).toBeVisible({ timeout: 3000 });

    // Find an OUTCOME node: look for nodes with the "Outcome" badge or leaf nodes
    // Outcome nodes have status-badge and typically are leaves (no chevron expander)
    // The seed data OUTCOME ID is 00000000-0000-0000-cccc-000000000001
    const outcomeNode = rcdoPanel.locator("[data-testid='tree-node-00000000-0000-0000-cccc-000000000001']");
    if (await outcomeNode.isVisible().catch(() => false)) {
      await outcomeNode.click();
    } else {
      // Fallback: click each node starting from the bottom until we get a selected indicator
      const count = await treeNodes.count();
      for (let i = count - 1; i >= 0; i--) {
        await treeNodes.nth(i).click();
        // Check if selection was accepted (rcdo-selected-node appears)
        const selectedNode = this.page.getByTestId("rcdo-selected-node");
        if (await selectedNode.isVisible().catch(() => false)) break;
      }
    }

    // Verify the RCDO was selected
    await this.page.getByTestId("rcdo-selected-node").waitFor({ timeout: 3000 }).catch(() => {});
  }

  /**
   * Add a commit with title, chess piece, optional points, and optional RCDO link.
   * When selectRcdo is true, opens the RCDO picker and selects an Outcome node.
   */
  async addCommit(
    title: string,
    chessPiece: string,
    points?: number,
    opts?: { selectRcdo?: boolean },
  ) {
    await this.openCommitForm();

    await this.page.getByTestId("commit-form-title").fill(title);
    await this.page
      .getByTestId(`chess-piece-option-${chessPiece.toLowerCase()}`)
      .click();
    if (points) {
      await this.page.getByTestId(`estimate-btn-${points}`).click();
    }

    if (opts?.selectRcdo) {
      await this.selectRcdoInPicker();
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
      timeout: 15000,
    });
  }
}
