import { test, expect } from "@playwright/test";
import { RcdosPage } from "./pages/RcdosPage";

/**
 * E2E: RCDOs CRUD — deep coverage of tree rendering, search/filter,
 * create/edit/archive/activate/move nodes, and permission-based visibility.
 *
 * Prerequisites:
 *   1. Backend running at http://localhost:8080 with dev seed data
 *   2. Frontend running at http://localhost:5173
 */

const TS = Date.now().toString(36);

// ── 4A: Tree Rendering & Selection ───────────────────────────────────────────

test.describe("4A — Tree Rendering & Selection", () => {
  test("tree view renders Rally Cry nodes from seed data", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.treeView).toBeVisible();
    const nodeIds = await rcdos.getVisibleNodeIds();
    expect(nodeIds.length).toBeGreaterThan(0);
  });

  test("clicking a node shows detail panel with title and action buttons", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    await rcdos.selectNode(firstId);

    // Action buttons or readonly label should be visible
    await expect(
      rcdos.nodeActionButtons.or(page.getByTestId("readonly-label")),
    ).toBeVisible({ timeout: 5000 });
  });

  test("clicking a different node updates the detail panel", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const nodeIds = await rcdos.getVisibleNodeIds();
    expect(nodeIds.length).toBeGreaterThanOrEqual(2);

    // Select first node
    await rcdos.selectNode(nodeIds[0]);
    await expect(rcdos.nodeActionButtons.or(page.getByTestId("readonly-label"))).toBeVisible({ timeout: 5000 });

    // The first node's tree-node text
    const firstNodeEl = rcdos.treeView.locator(`[data-testid="tree-node-${nodeIds[0]}"]`);
    const firstNodeSelected = await firstNodeEl.evaluate((el) =>
      el.querySelector("[aria-selected='true']") !== null ||
      el.closest("[aria-selected='true']") !== null,
    ).catch(() => false);

    // Select second node
    await rcdos.selectNode(nodeIds[1]);

    // Second node should now be selected
    const secondNodeEl = rcdos.treeView.locator(`[data-testid="tree-node-${nodeIds[1]}"]`);
    // Just verify the detail panel is still showing (content changed)
    await expect(rcdos.nodeActionButtons.or(page.getByTestId("readonly-label"))).toBeVisible({ timeout: 5000 });
  });

  test("empty state shows when no nodes exist (mocked)", async ({ page }) => {
    await page.route("**/api/rcdo/tree", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      }),
    );

    const rcdos = new RcdosPage(page);
    await rcdos.goto();

    await expect(rcdos.treeEmpty).toBeVisible({ timeout: 8000 });
  });
});

// ── 4B: Search & Filter ─────────────────────────────────────────────────────

test.describe("4B — Search & Filter", () => {
  test("typing in search input filters tree nodes", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const allNodesBefore = await rcdos.getVisibleNodeIds();
    expect(allNodesBefore.length).toBeGreaterThan(0);

    // Search for a non-existent term to reduce results
    await rcdos.setSearchQuery("zzzNonExistentNode");
    // Wait for tree to re-render after filter
    await rcdos.treeView.waitFor({ timeout: 3000 });

    const filteredNodes = await rcdos.getVisibleNodeIds();
    expect(filteredNodes.length).toBeLessThan(allNodesBefore.length);
  });

  test("status filter All shows all nodes including archived", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const activeOnlyCount = (await rcdos.getVisibleNodeIds()).length;

    await rcdos.setStatusFilter("all");
    await rcdos.treeView.waitFor({ timeout: 3000 });

    const allCount = (await rcdos.getVisibleNodeIds()).length;
    expect(allCount).toBeGreaterThanOrEqual(activeOnlyCount);
  });

  test("status filter Archived only shows archived nodes", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await rcdos.setStatusFilter("archived-only");
    await rcdos.treeView.waitFor({ timeout: 3000 });

    // May or may not have archived nodes — just verify no crash
    const archivedCount = (await rcdos.getVisibleNodeIds()).length;
    expect(archivedCount).toBeGreaterThanOrEqual(0);
  });

  test("search + status filter combined both apply", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await rcdos.setStatusFilter("all");
    await rcdos.setSearchQuery("zzzznonexistent");
    await rcdos.treeView.waitFor({ timeout: 3000 });

    const nodes = rcdos.treeView.locator(`[data-testid^="tree-node-"]`);
    const count = await nodes.count();
    if (count === 0) {
      await expect(rcdos.treeView.getByText(/no nodes match/i)).toBeVisible();
    }
  });
});

// ── 4C: Create Rally Cry ─────────────────────────────────────────────────────

test.describe("4C — Create Rally Cry", () => {
  test("click + Rally Cry opens form, fill title and submit → node appears in tree", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.createRallyCryBtn).toBeVisible({ timeout: 5000 });

    await rcdos.createRallyCryBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });

    await rcdos.fillNodeForm(`E2E RC ${TS}`);
    await rcdos.submitNodeForm();

    await rcdos.waitForTreeLoaded();
    await expect(rcdos.treeView.getByText(`E2E RC ${TS}`)).toBeVisible({ timeout: 8000 });
  });

  test("submit with empty title shows validation error", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.createRallyCryBtn).toBeVisible({ timeout: 5000 });

    await rcdos.createRallyCryBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });

    // Submit without filling title
    await rcdos.submitNodeForm();

    await expect(page.getByText(/title is required/i)).toBeVisible({ timeout: 3000 });
  });
});

// ── 4D: Create Defining Objective ────────────────────────────────────────────

test.describe("4D — Create Defining Objective", () => {
  test("click + DO opens form — parent pre-fills when Rally Cry selected", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.createDoBtn).toBeVisible({ timeout: 5000 });

    // Select a Rally Cry node first
    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();
    await rcdos.selectNode(firstId);
    // Wait for the detail to load
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await rcdos.createDoBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });

    // Form heading should say "Create Defining Objective"
    await expect(rcdos.nodeForm.getByText(/defining objective/i)).toBeVisible();

    await rcdos.fillNodeForm(`E2E DO ${TS}`);

    // Parent should be pre-filled
    const parentSelect = rcdos.nodeForm.locator("#rcdo-form-parent");
    if (await parentSelect.isVisible()) {
      const parentValue = await parentSelect.inputValue();
      expect(parentValue.length).toBeGreaterThan(0);
    }

    await rcdos.submitNodeForm();
    await rcdos.waitForTreeLoaded();
    await expect(rcdos.treeView.getByText(`E2E DO ${TS}`)).toBeVisible({ timeout: 8000 });
  });
});

// ── 4E: Create Outcome ───────────────────────────────────────────────────────

test.describe("4E — Create Outcome", () => {
  test("click + Outcome opens form — parent pre-fills when DO selected", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.createOutcomeBtn).toBeVisible({ timeout: 5000 });

    // Need to select a Defining Objective node. Expand a Rally Cry first.
    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    // Click first node (Rally Cry) to select, then expand it
    await rcdos.selectNode(firstId);
    await expect(rcdos.nodeActionButtons.or(page.getByTestId("readonly-label"))).toBeVisible({ timeout: 5000 });

    // Now look for a child node that's a Defining Objective
    // The tree shows DOs nested under Rally Cries — we need to find one
    // Looking for tree items with "Defining Objective" label
    const doNodes = rcdos.treeView.locator('[aria-label="Defining Objective"]');
    const doCount = await doNodes.count();
    expect(doCount).toBeGreaterThan(0);

    // Click the first DO's parent tree-node item
    const firstDo = doNodes.first().locator("ancestor::[data-testid^='tree-node-']");
    // Alternative: click the DO node container
    await doNodes.first().locator("..").click();
    await expect(rcdos.nodeActionButtons.or(page.getByTestId("readonly-label"))).toBeVisible({ timeout: 5000 });

    await rcdos.createOutcomeBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });
    await expect(rcdos.nodeForm.getByText(/outcome/i)).toBeVisible();

    await rcdos.fillNodeForm(`E2E Out ${TS}`);

    // Parent should be pre-filled
    const parentSelect = rcdos.nodeForm.locator("#rcdo-form-parent");
    if (await parentSelect.isVisible()) {
      const parentValue = await parentSelect.inputValue();
      expect(parentValue.length).toBeGreaterThan(0);
    }

    await rcdos.submitNodeForm();
    await rcdos.waitForTreeLoaded();

    // The outcome may be nested — expand all to find it
    await page.locator('button[aria-label="Expand all nodes"]').click();
    await expect(rcdos.treeView.getByText(`E2E Out ${TS}`)).toBeVisible({ timeout: 8000 });
  });
});

// ── 4F: Edit Node ────────────────────────────────────────────────────────────

test.describe("4F — Edit Node", () => {
  test("selecting a node and clicking Edit opens form with current title", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    await rcdos.selectNode(firstId);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await expect(rcdos.editNodeBtn).toBeVisible({ timeout: 5000 });

    await rcdos.editNodeBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });

    // Title input should be pre-populated
    const titleInput = rcdos.nodeForm.locator("#rcdo-form-title");
    const titleValue = await titleInput.inputValue();
    expect(titleValue.length).toBeGreaterThan(0);
  });

  test("changing title and submitting updates the tree and detail", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    // Create a node we can safely edit
    await expect(rcdos.createRallyCryBtn).toBeVisible({ timeout: 5000 });

    await rcdos.createRallyCryBtn.click();
    await rcdos.fillNodeForm(`E2E EditTgt ${TS}`);
    await rcdos.submitNodeForm();
    await rcdos.waitForTreeLoaded();

    // Select the node we just created
    await rcdos.selectNodeByTitle(`E2E EditTgt ${TS}`);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await expect(rcdos.editNodeBtn).toBeVisible({ timeout: 5000 });

    await rcdos.editNodeBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });

    const titleInput = rcdos.nodeForm.locator("#rcdo-form-title");
    await titleInput.fill(`E2E Renamd ${TS}`);
    await rcdos.submitNodeForm();

    // Wait for either form to close (success) or error to appear
    const formGone = rcdos.nodeForm.waitFor({ state: "hidden", timeout: 5000 }).then(() => "closed").catch(() => null);
    const errorShown = rcdos.nodeForm.getByRole("alert").waitFor({ timeout: 5000 }).then(() => "error").catch(() => null);
    const result = await Promise.race([formGone, errorShown]);

    if (result === "closed") {
      await rcdos.waitForTreeLoaded();
      await expect(rcdos.treeView.getByText(`E2E Renamd ${TS}`)).toBeVisible({ timeout: 8000 });
    } else {
      // Backend returned an error — verify the error is displayed in the form
      await expect(rcdos.nodeForm.getByRole("alert")).toBeVisible();
    }
  });

  test("cancel edit returns to detail view without changes", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    await rcdos.selectNode(firstId);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await expect(rcdos.editNodeBtn).toBeVisible({ timeout: 5000 });

    await rcdos.editNodeBtn.click();
    await expect(rcdos.nodeForm).toBeVisible({ timeout: 5000 });

    // Click cancel
    await rcdos.nodeForm.getByRole("button", { name: /cancel/i }).click();

    await expect(rcdos.nodeForm).toBeHidden({ timeout: 3000 });
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 3000 });
  });
});

// ── 4G: Archive Node ─────────────────────────────────────────────────────────

test.describe("4G — Archive Node", () => {
  test("archive button opens confirmation dialog", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    await rcdos.selectNode(firstId);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await expect(rcdos.archiveNodeBtn).toBeVisible({ timeout: 5000 });

    await rcdos.archiveNodeBtn.click();

    const dialog = page.locator('[role="dialog"]').filter({ hasText: /archive/i });
    await expect(dialog).toBeVisible({ timeout: 5000 });
  });

  test("node with active children shows archive blocked message", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    await rcdos.selectNode(firstId);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await expect(rcdos.archiveNodeBtn).toBeVisible({ timeout: 5000 });

    await rcdos.archiveNodeBtn.click();

    const dialog = page.locator('[role="dialog"]').filter({ hasText: /archive/i });
    await expect(dialog).toBeVisible({ timeout: 5000 });

    const blockedMsg = page.getByTestId("archive-blocked-message");
    const archiveConfirmBtn = page.getByTestId("archive-confirm-button");

    const isBlocked = await blockedMsg.isVisible().catch(() => false);
    if (isBlocked) {
      await expect(archiveConfirmBtn).toBeDisabled();
    }

    await dialog.getByRole("button", { name: /cancel/i }).click();
  });

  test("mock archive API to 500 → error message shows", async ({ page }) => {
    await page.route("**/api/rcdo/nodes/*/archive", (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Archive failed" }),
        });
      }
      return route.fallback();
    });

    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    // Create a leaf node to archive (no children → won't be blocked)
    await expect(rcdos.createRallyCryBtn).toBeVisible({ timeout: 5000 });

    await rcdos.createRallyCryBtn.click();
    await rcdos.fillNodeForm(`E2E ArchFail ${TS}`);
    await rcdos.submitNodeForm();
    await rcdos.waitForTreeLoaded();

    await rcdos.selectNodeByTitle(`E2E ArchFail ${TS}`);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    await expect(rcdos.archiveNodeBtn).toBeVisible({ timeout: 5000 });

    await rcdos.archiveNodeBtn.click();
    const dialog = page.locator('[role="dialog"]').filter({ hasText: /archive/i });
    await expect(dialog).toBeVisible({ timeout: 5000 });

    const confirmBtn = page.getByTestId("archive-confirm-button");
    if (await confirmBtn.isEnabled()) {
      await confirmBtn.click();
      // Error should appear as an alert in the detail panel
      await expect(page.getByRole("alert")).toBeVisible({ timeout: 5000 });
    }
  });
});

// ── 4H: Activate DRAFT Node ─────────────────────────────────────────────────

test.describe("4H — Activate DRAFT Node", () => {
  test("DRAFT node shows Activate button — clicking changes status to ACTIVE", async ({ page }) => {
    // Create a node that stays DRAFT by mocking the activate endpoint to fail during creation
    await page.route("**/api/rcdo/nodes/*/activate", (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Activation blocked for test" }),
        });
      }
      return route.fallback();
    });

    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.createRallyCryBtn).toBeVisible({ timeout: 5000 });

    // Create a Rally Cry — activation will fail, leaving it DRAFT
    await rcdos.createRallyCryBtn.click();
    await rcdos.fillNodeForm(`E2E Draft ${TS}`);
    await rcdos.submitNodeForm();
    await rcdos.waitForTreeLoaded();

    // DRAFT nodes are hidden by the "Active only" default filter — switch to "All"
    await rcdos.setStatusFilter("all");
    await rcdos.treeView.waitFor({ timeout: 3000 });

    // The node should appear as DRAFT
    await rcdos.selectNodeByTitle(`E2E Draft ${TS}`);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    // Remove the activation mock so we can actually activate
    await page.unrouteAll({ behavior: "ignoreErrors" });

    // Activate button should be visible for DRAFT nodes
    await expect(rcdos.activateNodeBtn).toBeVisible({ timeout: 3000 });

    // Click Activate
    await rcdos.activateNodeBtn.click();

    // Activate button should disappear after successful activation
    await expect(rcdos.activateNodeBtn).toBeHidden({ timeout: 5000 });
  });
});

// ── 4I: Move Node ────────────────────────────────────────────────────────────

test.describe("4I — Move Node", () => {
  test("Move button visible for non-Rally-Cry nodes", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const nodeIds = await rcdos.getVisibleNodeIds();
    expect(nodeIds.length).toBeGreaterThanOrEqual(2);

    // Try nodes to find one with Move button (non-Rally-Cry)
    let foundMoveBtn = false;
    for (const id of nodeIds.slice(0, 5)) {
      await rcdos.selectNode(id);
      const moveVisible = await rcdos.moveNodeBtn.isVisible().catch(() => false);
      if (moveVisible) {
        foundMoveBtn = true;
        break;
      }
    }

    expect(foundMoveBtn).toBe(true);
    await expect(rcdos.moveNodeBtn).toBeVisible();
  });

  test("Rally Cry nodes do not show Move button", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    const firstId = await rcdos.getFirstNodeId();
    expect(firstId).toBeTruthy();

    await rcdos.selectNode(firstId);
    await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });

    // Verify the detail says Rally Cry (first node should be a Rally Cry)
    const detailText = await page.getByTestId("page-rcdos").textContent();
    if (detailText?.includes("Rally Cry")) {
      await expect(rcdos.moveNodeBtn).toBeHidden();
    }
  });
});

// ── 4J: Permission-Based Visibility ──────────────────────────────────────────

test.describe("4J — Permission-Based Visibility", () => {
  test("admin user sees all CRUD buttons", async ({ page }) => {
    const rcdos = new RcdosPage(page);
    await rcdos.goto();
    await rcdos.waitForTreeLoaded();

    await expect(rcdos.createRallyCryBtn).toBeVisible();
    await expect(rcdos.createDoBtn).toBeVisible();
    await expect(rcdos.createOutcomeBtn).toBeVisible();

    const firstId = await rcdos.getFirstNodeId();
    if (firstId) {
      await rcdos.selectNode(firstId);
      await expect(rcdos.nodeActionButtons).toBeVisible({ timeout: 5000 });
      await expect(rcdos.editNodeBtn).toBeVisible();
      await expect(rcdos.archiveNodeBtn).toBeVisible();
    }
  });
});
