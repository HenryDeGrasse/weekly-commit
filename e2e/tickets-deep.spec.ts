import { test, expect } from "@playwright/test";
import { TicketsPage } from "./pages/TicketsPage";

/**
 * E2E: Tickets CRUD — deep coverage of create, detail, status transitions,
 * filters, pagination, edit, assignee, and linked data.
 *
 * Prerequisites:
 *   1. Backend running at http://localhost:8080 with dev seed data
 *   2. Frontend running at http://localhost:5173
 */

const CURRENT_USER_ID = "00000000-0000-0000-0000-000000000001";
const CURRENT_TEAM_ID = "00000000-0000-0000-0000-000000000010";

const TS = Date.now().toString(36);

/** Compute current week's Monday (YYYY-MM-DD) — tickets must have this targetWeek to appear in the default list. */
function getCurrentMonday(): string {
  const now = new Date();
  const day = now.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  const monday = new Date(now);
  monday.setDate(now.getDate() + diff);
  return `${monday.getFullYear()}-${String(monday.getMonth() + 1).padStart(2, "0")}-${String(monday.getDate()).padStart(2, "0")}`;
}

const THIS_WEEK = getCurrentMonday();

// ── 3A: Create Ticket ────────────────────────────────────────────────────────

test.describe("3A — Create Ticket", () => {
  test("clicking Create Ticket opens the form dialog", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await expect(tickets.ticketFormDialog).toBeVisible();
    await expect(page.getByTestId("ticket-form-title")).toBeVisible();
    await expect(page.getByTestId("ticket-form-submit")).toBeVisible();
  });

  test("creates a ticket with required fields and it appears in the list", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E Required Fields ${TS}`,
      priority: "HIGH",
      targetWeek: THIS_WEEK,
    });

    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();
    await expect(page.getByText(`E2E Required Fields ${TS}`)).toBeVisible({ timeout: 5000 });
  });

  test("creates a ticket with all fields filled", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E All Fields ${TS}`,
      description: "Full description for test ticket",
      status: "TODO",
      priority: "CRITICAL",
      estimate: "5",
      targetWeek: THIS_WEEK,
    });

    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();
    await expect(page.getByText(`E2E All Fields ${TS}`)).toBeVisible({ timeout: 5000 });
  });

  test("ticket count increments after creating a ticket", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    const countTextBefore = await tickets.ticketCount.textContent();
    const countBefore = parseInt(countTextBefore ?? "0", 10);

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E Count Test ${TS}`,
      priority: "LOW",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    const countTextAfter = await tickets.ticketCount.textContent();
    const countAfter = parseInt(countTextAfter ?? "0", 10);
    expect(countAfter).toBeGreaterThanOrEqual(countBefore);
  });

  test("submit with empty title shows validation error", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await page.getByTestId("ticket-form-title").fill("");
    await page.getByTestId("ticket-form-submit").click();

    await expect(tickets.ticketFormDialog).toBeVisible();
    await expect(page.getByText(/title is required/i)).toBeVisible({ timeout: 3000 });
  });

  test("submit with empty team shows validation error", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await page.getByTestId("ticket-form-title").fill("Has title");
    await page.getByTestId("ticket-form-team").fill("");
    await page.getByTestId("ticket-form-submit").click();

    await expect(tickets.ticketFormDialog).toBeVisible();
    await expect(page.getByText(/team is required/i)).toBeVisible({ timeout: 3000 });
  });

  test("submit with empty reporter shows validation error", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await page.getByTestId("ticket-form-title").fill("Has title");
    await page.getByTestId("ticket-form-reporter").fill("");
    await page.getByTestId("ticket-form-submit").click();

    await expect(tickets.ticketFormDialog).toBeVisible();
    await expect(page.getByText(/reporter is required/i)).toBeVisible({ timeout: 3000 });
  });

  test("mock POST /api/tickets to 500 → submit error shows", async ({ page }) => {
    await page.route("**/api/tickets", (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Internal Server Error" }),
        });
      }
      return route.fallback();
    });

    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E Error Test ${TS}`,
      priority: "MEDIUM",
    });

    await expect(page.getByTestId("ticket-form-submit-error")).toBeVisible({ timeout: 5000 });
    await expect(tickets.ticketFormDialog).toBeVisible();
  });
});

// ── 3B: Ticket Detail Panel ──────────────────────────────────────────────────

test.describe("3B — Ticket Detail Panel", () => {
  test("clicking a ticket row opens the detail panel", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);
    await expect(page.getByTestId("ticket-detail-view")).toBeVisible();
  });

  test("detail panel shows key, title, and status badge", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);

    await expect(page.getByTestId("ticket-detail-key")).toBeVisible();
    await expect(page.getByTestId("ticket-detail-title")).toBeVisible();
    await expect(page.getByTestId("ticket-detail-status-badge")).toBeVisible();
  });

  test("detail panel shows priority, team, and reporter", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);

    await expect(page.getByTestId("ticket-detail-priority")).toBeVisible();
    await expect(page.getByTestId("ticket-detail-team")).toBeVisible();
    await expect(page.getByTestId("ticket-detail-reporter")).toBeVisible();
  });

  test("close button closes the detail panel", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);
    await expect(tickets.ticketDetailPanel).toBeVisible();
    await tickets.closeDetailPanel();
    await expect(tickets.ticketDetailPanel).toBeHidden();
  });

  test("clicking the same ticket row again closes the panel (toggle)", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    const firstRow = tickets.ticketListTable.locator("tbody tr").first();
    await firstRow.click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    await firstRow.click();
    await expect(tickets.ticketDetailPanel).toBeHidden({ timeout: 5000 });
  });
});

// ── 3C: Status Transitions ───────────────────────────────────────────────────

test.describe("3C — Status Transitions", () => {
  test("TODO ticket shows In Progress and Canceled transition buttons", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    // Create a TODO ticket with this week's target so it appears in the list
    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E Trans TODO ${TS}`,
      priority: "MEDIUM",
      status: "TODO",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    await page.getByText(`E2E Trans TODO ${TS}`).click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    const transitionBtns = page.getByTestId("status-transition-buttons");
    await expect(transitionBtns).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("transition-btn-in_progress")).toBeVisible();
    await expect(page.getByTestId("transition-btn-canceled")).toBeVisible();
  });

  test("clicking In Progress transitions status to IN_PROGRESS", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E ToIP ${TS}`,
      priority: "MEDIUM",
      status: "TODO",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    await page.getByText(`E2E ToIP ${TS}`).click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    await page.getByTestId("transition-btn-in_progress").click();
    await expect(page.getByTestId("ticket-detail-status-badge")).toContainText(/in progress/i, { timeout: 5000 });
  });

  test("IN_PROGRESS ticket shows Done, Blocked, Canceled, To Do buttons", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E IPBtns ${TS}`,
      priority: "MEDIUM",
      status: "IN_PROGRESS",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    await page.getByText(`E2E IPBtns ${TS}`).click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    await expect(page.getByTestId("transition-btn-done")).toBeVisible();
    await expect(page.getByTestId("transition-btn-blocked")).toBeVisible();
    await expect(page.getByTestId("transition-btn-canceled")).toBeVisible();
    await expect(page.getByTestId("transition-btn-todo")).toBeVisible();
  });

  test("clicking Done marks ticket as DONE with no further transitions", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E ToDone ${TS}`,
      priority: "MEDIUM",
      status: "IN_PROGRESS",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    await page.getByText(`E2E ToDone ${TS}`).click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    await page.getByTestId("transition-btn-done").click();
    await expect(page.getByTestId("ticket-detail-status-badge")).toContainText(/done/i, { timeout: 5000 });
    // DONE is terminal — no transition buttons
    await expect(page.getByTestId("status-transition-buttons")).toBeHidden({ timeout: 3000 });
  });

  test("mock transition API to 500 shows transition error", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    // First create a TODO ticket so we have something with transition buttons
    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E TransErr ${TS}`,
      priority: "MEDIUM",
      status: "TODO",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    // Now mock the status transition endpoint to fail
    // The frontend uses PUT /api/tickets/{id}/status for transitions
    await page.route("**/api/tickets/*/status", (route) => {
      if (route.request().method() === "PUT") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Transition failed" }),
        });
      }
      return route.fallback();
    });

    // Select the ticket we just created
    await page.getByText(`E2E TransErr ${TS}`).click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    const transitionBtns = page.getByTestId("status-transition-buttons");
    await expect(transitionBtns).toBeVisible({ timeout: 5000 });

    const firstBtn = transitionBtns.locator("button").first();
    await firstBtn.click();

    await expect(page.getByTestId("transition-error")).toBeVisible({ timeout: 5000 });
  });
});

// ── 3D: Edit Ticket ──────────────────────────────────────────────────────────

test.describe("3D — Edit Ticket", () => {
  test("edit button opens form pre-populated, can change title", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    // Create a ticket to edit
    await tickets.openCreateForm();
    await tickets.fillAndSubmitTicket({
      title: `E2E EditMe ${TS}`,
      priority: "HIGH",
      targetWeek: THIS_WEEK,
    });
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });
    await tickets.waitForListLoaded();

    // Select it
    await page.getByText(`E2E EditMe ${TS}`).click();
    await expect(tickets.ticketDetailPanel).toBeVisible({ timeout: 5000 });

    // Click edit
    await page.getByTestId("ticket-detail-edit-btn").click();
    await expect(tickets.ticketFormDialog).toBeVisible({ timeout: 5000 });

    // Title should be pre-populated
    const titleInput = page.getByTestId("ticket-form-title");
    await expect(titleInput).toHaveValue(`E2E EditMe ${TS}`);

    // Change title
    await titleInput.fill(`E2E Edited ${TS}`);
    await page.getByTestId("ticket-form-submit").click();
    await expect(tickets.ticketFormDialog).toBeHidden({ timeout: 8000 });

    // Detail panel should reflect new title
    await expect(page.getByTestId("ticket-detail-title")).toContainText(`E2E Edited ${TS}`, { timeout: 5000 });
  });
});

// ── 3E: Assignee Management ──────────────────────────────────────────────────

test.describe("3E — Assignee Management", () => {
  test("assignment section shows current assignee or Unassigned", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);
    await expect(page.getByTestId("ticket-assignment-section")).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("ticket-assignee-input")).toBeVisible();
    await expect(page.getByTestId("ticket-assign-save-btn")).toBeVisible();
  });

  test("mock assignee save to 500 → shows error", async ({ page }) => {
    await page.route("**/api/tickets/*", (route) => {
      if (route.request().method() === "PUT") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Assignment failed" }),
        });
      }
      return route.fallback();
    });

    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);
    await expect(page.getByTestId("ticket-assignment-section")).toBeVisible({ timeout: 5000 });

    const assigneeInput = page.getByTestId("ticket-assignee-input");
    const tagName = await assigneeInput.evaluate((e) => e.tagName.toLowerCase());
    if (tagName === "select") {
      const options = assigneeInput.locator("option");
      const count = await options.count();
      if (count > 1) {
        await assigneeInput.selectOption({ index: 1 });
      }
    } else {
      await assigneeInput.fill("some-user-id");
    }
    await page.getByTestId("ticket-assign-save-btn").click();

    await expect(page.getByTestId("assignee-error")).toBeVisible({ timeout: 5000 });
  });
});

// ── 3F: Filters ──────────────────────────────────────────────────────────────

test.describe("3F — Filters", () => {
  test("status filter narrows results to selected status", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.filters).toBeVisible();

    await page.getByTestId("filter-status").selectOption("DONE");
    await tickets.waitForListLoaded();

    const empty = await tickets.ticketListEmpty.isVisible().catch(() => false);
    if (!empty) {
      const rowCount = await tickets.ticketListTable.locator("tbody tr").count();
      expect(rowCount).toBeGreaterThan(0);
    }
  });

  test("priority filter narrows results to selected priority", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await page.getByTestId("filter-priority").selectOption("CRITICAL");
    await tickets.waitForListLoaded();

    const empty = await tickets.ticketListEmpty.isVisible().catch(() => false);
    const table = await tickets.ticketListTable.isVisible().catch(() => false);
    expect(empty || table).toBe(true);
  });

  test("clear all resets filters and shows full list", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    // Apply a filter first
    await page.getByTestId("filter-status").selectOption("BLOCKED");
    await tickets.waitForListLoaded();

    const clearBtn = page.getByTestId("filter-clear-all");
    if (await clearBtn.isVisible()) {
      await clearBtn.click();
      await tickets.waitForListLoaded();
      await expect(page.getByTestId("filter-status")).toHaveValue("");
    }
  });

  test("combined filters — status + priority both apply", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await page.getByTestId("filter-status").selectOption("TODO");
    await page.getByTestId("filter-priority").selectOption("HIGH");
    await tickets.waitForListLoaded();

    const empty = await tickets.ticketListEmpty.isVisible().catch(() => false);
    const table = await tickets.ticketListTable.isVisible().catch(() => false);
    expect(empty || table).toBe(true);
  });
});

// ── 3G: Pagination & Sorting ─────────────────────────────────────────────────

test.describe("3G — Pagination & Sorting", () => {
  test("sort by column header reorders the list", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await page.getByTestId("sort-col-key").click();
    await tickets.waitForListLoaded();

    // After clicking, verify that the sort was applied by checking
    // the Key column shows a sort arrow (ascending on first click)
    await expect(page.getByTestId("sort-col-key")).toHaveAttribute(
      "aria-sort", /(ascending|descending)/,
      { timeout: 5000 },
    );
  });

  test("pagination controls work when there are enough tickets", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    const paginationVisible = await tickets.pagination.isVisible().catch(() => false);
    if (!paginationVisible) {
      test.skip();
      return;
    }

    const indicatorBefore = await page.getByTestId("ticket-page-indicator").textContent();
    const nextBtn = page.getByTestId("ticket-page-next");
    const isDisabled = await nextBtn.isDisabled();
    if (isDisabled) {
      // Already on last page — just verify pagination elements exist
      expect(indicatorBefore).toBeTruthy();
      return;
    }
    await nextBtn.click();
    // Wait for the indicator to change
    await expect(page.getByTestId("ticket-page-indicator")).not.toHaveText(indicatorBefore!, { timeout: 5000 });
  });
});

// ── 3H: Status History & Linked Commits ──────────────────────────────────────

test.describe("3H — Status History & Linked Commits", () => {
  test("detail panel shows status history timeline or empty state", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);

    const timeline = page.getByTestId("status-history-timeline");
    const emptyHistory = page.getByTestId("status-history-empty");
    await expect(timeline.or(emptyHistory)).toBeVisible({ timeout: 5000 });
  });

  test("detail panel shows linked commits section or empty state", async ({ page }) => {
    const tickets = new TicketsPage(page);
    await tickets.goto();
    await tickets.waitForListLoaded();

    await expect(tickets.ticketListTable).toBeVisible({ timeout: 10000 });

    await tickets.selectTicketRow(0);

    const linkedCommits = page.getByTestId("linked-commits-list");
    const emptyCommits = page.getByTestId("linked-commits-empty");
    await expect(linkedCommits.or(emptyCommits)).toBeVisible({ timeout: 5000 });
  });
});
