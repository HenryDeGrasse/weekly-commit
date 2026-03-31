import { type Page, type Locator, expect } from "@playwright/test";

/**
 * Page Object Model for the RCDOs page.
 */
export class RcdosPage {
  readonly page: Page;
  readonly pageContainer: Locator;
  readonly treeView: Locator;
  readonly treeEmpty: Locator;
  readonly nodeForm: Locator;
  readonly createRallyCryBtn: Locator;
  readonly createDoBtn: Locator;
  readonly createOutcomeBtn: Locator;
  readonly editNodeBtn: Locator;
  readonly archiveNodeBtn: Locator;
  readonly activateNodeBtn: Locator;
  readonly moveNodeBtn: Locator;
  readonly nodeActionButtons: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageContainer = page.getByTestId("page-rcdos");
    this.treeView = page.getByTestId("rcdo-tree-view");
    this.treeEmpty = page.getByTestId("rcdo-tree-empty");
    this.nodeForm = page.getByTestId("rcdo-node-form");
    this.createRallyCryBtn = page.getByTestId("create-rally-cry-btn");
    this.createDoBtn = page.getByTestId("create-do-btn");
    this.createOutcomeBtn = page.getByTestId("create-outcome-btn");
    this.editNodeBtn = page.getByTestId("edit-node-btn");
    this.archiveNodeBtn = page.getByTestId("archive-node-btn");
    this.activateNodeBtn = page.getByTestId("activate-node-btn");
    this.moveNodeBtn = page.getByTestId("move-node-btn");
    this.nodeActionButtons = page.getByTestId("node-action-buttons");
  }

  async goto() {
    await this.page.goto("/weekly/rcdos");
    await this.pageContainer.waitFor({ timeout: 10000 });
  }

  async waitForTreeLoaded() {
    await expect(
      this.treeView.or(this.treeEmpty),
    ).toBeVisible({ timeout: 10000 });
  }

  async selectNode(nodeId: string) {
    const nodeItem = this.page.getByTestId(`tree-node-${nodeId}`);
    await nodeItem.click();
  }

  /** Click a tree node by its visible title text. */
  async selectNodeByTitle(title: string) {
    // Find the tree node item containing this title
    const node = this.treeView.locator(`[data-testid^="tree-node-"]`).filter({ hasText: title }).first();
    await node.click();
  }

  /** Get the first tree node testid from the tree. */
  async getFirstNodeId(): Promise<string | null> {
    const nodes = this.treeView.locator(`[data-testid^="tree-node-"]`);
    const count = await nodes.count();
    if (count === 0) return null;
    const testId = await nodes.first().getAttribute("data-testid");
    return testId ? testId.replace("tree-node-", "") : null;
  }

  /** Get all visible tree node IDs. */
  async getVisibleNodeIds(): Promise<string[]> {
    const nodes = this.treeView.locator(`[data-testid^="tree-node-"]`);
    const count = await nodes.count();
    const ids: string[] = [];
    for (let i = 0; i < count; i++) {
      const testId = await nodes.nth(i).getAttribute("data-testid");
      if (testId) ids.push(testId.replace("tree-node-", ""));
    }
    return ids;
  }

  async fillNodeForm(title: string) {
    await expect(this.nodeForm).toBeVisible({ timeout: 5000 });
    const titleInput = this.nodeForm.locator("#rcdo-form-title");
    await titleInput.fill(title);
  }

  async submitNodeForm() {
    await this.nodeForm.locator('button[type="submit"]').click();
  }

  async setSearchQuery(query: string) {
    const searchInput = this.page.locator('#rcdo-search');
    await searchInput.fill(query);
  }

  async setStatusFilter(filter: "all" | "active-only" | "archived-only") {
    const radio = this.page.locator(`input[name="rcdo-status-filter"][value="${filter}"]`);
    await radio.click();
  }
}
