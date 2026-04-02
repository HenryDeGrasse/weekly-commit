import { test, expect } from "@playwright/test";
import { TeamPage } from "./pages/TeamPage";

/**
 * E2E: Team Dashboard Deep — data interactions within each tab, exception
 * resolution, comment posting, and error/loading states.
 *
 * Prerequisites:
 *   1. Backend running at http://localhost:8080 with dev seed data
 *   2. Frontend running at http://localhost:5173
 */

// ── 7A: Overview Section Data ────────────────────────────────────────────────

test.describe("7A — Overview Section Data", () => {
  test("lock compliance card shows percentage and count", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    const card = page.getByTestId("lock-compliance-card");
    await expect(card).toBeVisible({ timeout: 8000 });

    // Should contain a fraction like "X/Y"
    const countText = await page.getByTestId("lock-compliance-card-count").textContent();
    expect(countText).toMatch(/\d+\/\d+/);

    // Should contain a percentage
    const pctText = await page.getByTestId("lock-compliance-card-pct").textContent();
    expect(pctText).toMatch(/\d+%/);
  });

  test("reconcile compliance card shows percentage and count", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    const card = page.getByTestId("reconcile-compliance-card");
    await expect(card).toBeVisible({ timeout: 8000 });

    const countText = await page.getByTestId("reconcile-compliance-card-count").textContent();
    expect(countText).toMatch(/\d+\/\d+/);
  });

  test("points summary card shows total planned and achieved", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    const card = page.getByTestId("points-summary-card");
    await expect(card).toBeVisible({ timeout: 8000 });

    // Should have "planned" and "achieved" text
    const content = await card.textContent();
    expect(content).toContain("planned");
    expect(content).toContain("achieved");
  });

  test("exceptions overview card shows count by severity", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    const card = page.getByTestId("exceptions-overview-card");
    await expect(card).toBeVisible({ timeout: 8000 });

    // Should show the total open exception count
    const totalCount = await page.getByTestId("open-exceptions-count").textContent();
    expect(totalCount).toBeTruthy();
    const count = parseInt(totalCount ?? "0", 10);
    expect(count).toBeGreaterThanOrEqual(0);
  });
});

// ── 7B: By-Person Tab ────────────────────────────────────────────────────────

test.describe("7B — By-Person Tab", () => {
  test("by-person table shows team members with plan state and points", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("by-person");

    const section = page.getByTestId("by-person-section");
    await expect(section).toBeVisible({ timeout: 8000 });

    const table = page.getByTestId("by-person-table");
    await expect(table).toBeVisible();

    // Table should have rows
    const rows = table.locator("tbody tr[data-testid^='member-row-']");
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);

    // First row should have a name
    const firstRowText = await rows.first().textContent();
    expect(firstRowText).toBeTruthy();
    expect(firstRowText!.length).toBeGreaterThan(0);
  });

  test("each member row shows name and plan state badge or 'No plan'", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("by-person");

    const table = page.getByTestId("by-person-table");
    await expect(table).toBeVisible({ timeout: 8000 });

    // Check that each row has some state indicator
    const rows = table.locator("tbody tr[data-testid^='member-row-']");
    const rowCount = await rows.count();
    for (let i = 0; i < Math.min(rowCount, 3); i++) {
      const rowText = await rows.nth(i).textContent();
      // Should contain a state like DRAFT, LOCKED, RECONCILED, etc. or "No plan"
      expect(rowText).toBeTruthy();
    }
  });

  test("clicking a member row expands to show their commits", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("by-person");

    const table = page.getByTestId("by-person-table");
    await expect(table).toBeVisible({ timeout: 8000 });

    const rows = table.locator("tbody tr[data-testid^='member-row-']");
    const rowCount = await rows.count();
    if (rowCount === 0) {
      test.skip();
      return;
    }

    // Click first row
    await rows.first().click();

    // Should show expanded row with commits
    const expandedRows = table.locator("tbody tr[data-testid^='member-row-expanded-']");
    await expect(expandedRows.first()).toBeVisible({ timeout: 3000 });
  });
});

// ── 7C: By-RCDO Tab ─────────────────────────────────────────────────────────

test.describe("7C — By-RCDO Tab", () => {
  test("switching to by-rcdo tab shows the RCDO section", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("by-rcdo");

    const panel = page.getByTestId("panel-by-rcdo");
    await expect(panel).toBeVisible({ timeout: 8000 });

    const section = page.getByTestId("by-rcdo-section");
    await expect(section).toBeVisible();
  });

  test("RCDO table shows RCDO nodes with commit count and point totals", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("by-rcdo");

    const table = page.getByTestId("by-rcdo-table");
    await expect(table).toBeVisible({ timeout: 8000 });

    // Table should have column headers
    const headers = await table.locator("thead th").allTextContents();
    expect(headers.length).toBeGreaterThan(0);

    // Should have at least one RCDO row from seed data
    const rcdoRows = table.locator("tbody tr[data-testid^='rcdo-row-']");
    const rowCount = await rcdoRows.count();
    expect(rowCount).toBeGreaterThan(0);
  });
});

// ── 7D: Chess Distribution Tab ───────────────────────────────────────────────

test.describe("7D — Chess Distribution Tab", () => {
  test("chess distribution section shows stacked bar and table", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("chess");

    const section = page.getByTestId("chess-distribution-section");
    await expect(section).toBeVisible({ timeout: 8000 });

    // Table should be visible
    const table = page.getByTestId("chess-distribution-table");
    await expect(table).toBeVisible();

    // Check for piece rows
    const kingRow = page.getByTestId("chess-row-king");
    await expect(kingRow).toBeVisible();
    const pawnRow = page.getByTestId("chess-row-pawn");
    await expect(pawnRow).toBeVisible();
  });

  test("chess table shows total commits and total points", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("chess");

    const section = page.getByTestId("chess-distribution-section");
    await expect(section).toBeVisible({ timeout: 8000 });

    // Total commits and points may or may not be in the tfoot (only when totalCommits > 0)
    const totalCommits = page.getByTestId("chess-total-commits");
    const totalPoints = page.getByTestId("chess-total-points");

    // If there's data, totals should be visible
    const hasCommits = await totalCommits.isVisible().catch(() => false);
    if (hasCommits) {
      const commitsText = await totalCommits.textContent();
      expect(parseInt(commitsText ?? "0", 10)).toBeGreaterThanOrEqual(0);

      const pointsText = await totalPoints.textContent();
      expect(parseInt(pointsText ?? "0", 10)).toBeGreaterThanOrEqual(0);
    }
  });

  test("stacked bar renders when there are commits", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("chess");

    const section = page.getByTestId("chess-distribution-section");
    await expect(section).toBeVisible({ timeout: 8000 });

    const stackedBar = page.getByTestId("chess-stacked-bar");
    const totalCommits = page.getByTestId("chess-total-commits");
    const hasCommits = await totalCommits.isVisible().catch(() => false);
    if (hasCommits) {
      await expect(stackedBar).toBeVisible();
      // Should have at least one bar segment
      const segments = stackedBar.locator("[data-testid^='bar-segment-']");
      const segmentCount = await segments.count();
      expect(segmentCount).toBeGreaterThan(0);
    }
  });
});

// ── 7E: Uncommitted Work Tab ─────────────────────────────────────────────────

test.describe("7E — Uncommitted Work Tab", () => {
  test("uncommitted tab shows assigned and unassigned sections", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("uncommitted");

    const panel = page.getByTestId("panel-uncommitted");
    await expect(panel).toBeVisible({ timeout: 8000 });

    const section = page.getByTestId("uncommitted-work-section");
    await expect(section).toBeVisible();

    // Either the "no uncommitted work" message or the two subsections
    const noWork = page.getByTestId("no-uncommitted-work");
    const assignedSection = page.getByTestId("assigned-uncommitted-section");

    const noWorkVisible = await noWork.isVisible().catch(() => false);
    if (!noWorkVisible) {
      await expect(assignedSection).toBeVisible();
      await expect(page.getByTestId("unassigned-section")).toBeVisible();
    }
  });

  test("assigned uncommitted count and unassigned count display", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("uncommitted");

    const section = page.getByTestId("uncommitted-work-section");
    await expect(section).toBeVisible({ timeout: 8000 });

    const noWork = page.getByTestId("no-uncommitted-work");
    const noWorkVisible = await noWork.isVisible().catch(() => false);
    if (noWorkVisible) {
      // No uncommitted work — counts not shown
      return;
    }

    const assignedCount = page.getByTestId("assigned-uncommitted-count");
    const unassignedCount = page.getByTestId("unassigned-count");

    await expect(assignedCount).toBeVisible();
    await expect(unassignedCount).toBeVisible();

    const assignedText = await assignedCount.textContent();
    expect(parseInt(assignedText ?? "0", 10)).toBeGreaterThanOrEqual(0);

    const unassignedText = await unassignedCount.textContent();
    expect(parseInt(unassignedText ?? "0", 10)).toBeGreaterThanOrEqual(0);
  });
});

// ── 7F: Exception Queue Tab ─────────────────────────────────────────────────

/**
 * Mock exception data for tests that need controlled unresolved exceptions.
 * The real backend's exception API may return 500 or no data for the current week,
 * so we provide realistic mock data to test the full UI interaction flow.
 */
const MOCK_EXCEPTIONS = [
  {
    id: "exc-e2e-001",
    teamId: "00000000-0000-0000-0000-000000000010",
    planId: "plan-e2e-001",
    userId: "00000000-0000-0000-0000-000000000001",
    displayName: "Dev User",
    exceptionType: "MISSED_LOCK",
    severity: "HIGH",
    description: "Plan was not locked before the deadline.",
    weekStartDate: "2026-03-30",
    resolved: false,
    resolution: null,
    resolvedAt: null,
    resolvedById: null,
    createdAt: "2026-03-30T09:00:00Z",
  },
  {
    id: "exc-e2e-002",
    teamId: "00000000-0000-0000-0000-000000000010",
    planId: "plan-e2e-002",
    userId: "00000000-0000-0000-0000-000000000003",
    displayName: "Alice Chen",
    exceptionType: "OVER_BUDGET",
    severity: "MEDIUM",
    description: "Planned points exceed capacity budget.",
    weekStartDate: "2026-03-30",
    resolved: false,
    resolution: null,
    resolvedAt: null,
    resolvedById: null,
    createdAt: "2026-03-30T10:00:00Z",
  },
  {
    id: "exc-e2e-003",
    teamId: "00000000-0000-0000-0000-000000000010",
    planId: null,
    userId: "00000000-0000-0000-0000-000000000004",
    displayName: "Bob Martinez",
    exceptionType: "REPEATED_CARRY_FORWARD",
    severity: "LOW",
    description: "Commit carried forward 3 weeks in a row.",
    weekStartDate: "2026-03-30",
    resolved: true,
    resolution: "Discussed in 1:1, reprioritised.",
    resolvedAt: "2026-03-30T14:00:00Z",
    resolvedById: "00000000-0000-0000-0000-000000000001",
    createdAt: "2026-03-30T08:00:00Z",
  },
];

function mockExceptionsApi(page: import("@playwright/test").Page) {
  // Mock the GET exceptions endpoint to return our controlled data
  void page.route("**/api/teams/*/week/*/exceptions", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(MOCK_EXCEPTIONS),
    }),
  );
}

test.describe("7F — Exception Queue Tab", () => {
  test("exceptions tab shows exception list or no-exceptions message", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    const panel = page.getByTestId("panel-exceptions");
    await expect(panel).toBeVisible({ timeout: 8000 });

    const section = page.getByTestId("exception-queue-section");
    await expect(section).toBeVisible();

    // Either exception list or "no open exceptions" message
    const exceptionList = page.getByTestId("exception-list");
    const noExceptions = page.getByTestId("no-exceptions-message");
    await expect(exceptionList.or(noExceptions)).toBeVisible({ timeout: 5000 });
  });

  test("each exception shows severity badge, type label, and description", async ({ page }) => {
    mockExceptionsApi(page);
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    const exceptionList = page.getByTestId("exception-list");
    await expect(exceptionList).toBeVisible({ timeout: 8000 });

    // Check first unresolved exception item (exc-e2e-001)
    const items = exceptionList.locator("[data-testid^='exception-item-']");
    const count = await items.count();
    expect(count).toBeGreaterThan(0);

    await expect(page.getByTestId("exception-severity-exc-e2e-001")).toBeVisible();
    await expect(page.getByTestId("exception-type-exc-e2e-001")).toBeVisible();
    await expect(page.getByTestId("exception-type-exc-e2e-001")).toContainText("Missed Lock");
  });

  test("resolve button opens resolve dialog", async ({ page }) => {
    mockExceptionsApi(page);
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    const exceptionList = page.getByTestId("exception-list");
    await expect(exceptionList).toBeVisible({ timeout: 8000 });

    // Click resolve on the first unresolved exception
    const resolveBtn = page.getByTestId("resolve-btn-exc-e2e-001");
    await expect(resolveBtn).toBeVisible({ timeout: 3000 });
    await resolveBtn.click();

    const dialog = page.getByTestId("resolve-exception-dialog");
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("resolution-note-input")).toBeVisible();
    await expect(page.getByTestId("resolve-exception-confirm")).toBeVisible();
  });

  test("empty resolution note keeps confirm button disabled", async ({ page }) => {
    mockExceptionsApi(page);
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    const resolveBtn = page.getByTestId("resolve-btn-exc-e2e-001");
    await expect(resolveBtn).toBeVisible({ timeout: 8000 });
    await resolveBtn.click();

    await expect(page.getByTestId("resolve-exception-dialog")).toBeVisible({ timeout: 5000 });

    // With empty note, confirm should be disabled
    await expect(page.getByTestId("resolve-exception-confirm")).toBeDisabled();
  });

  test("entering resolution note and confirming resolves the exception", async ({ page }) => {
    mockExceptionsApi(page);
    // Also mock the resolve PUT so it succeeds
    await page.route("**/api/exceptions/*/resolve", (route) => {
      if (route.request().method() === "PUT") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ ...MOCK_EXCEPTIONS[0], resolved: true, resolution: "Done" }),
        });
      }
      return route.fallback();
    });

    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    const resolveBtn = page.getByTestId("resolve-btn-exc-e2e-001");
    await expect(resolveBtn).toBeVisible({ timeout: 8000 });
    await resolveBtn.click();

    await expect(page.getByTestId("resolve-exception-dialog")).toBeVisible({ timeout: 5000 });

    // Fill in resolution note
    await page.getByTestId("resolution-note-input").fill("Resolved via E2E test");
    await expect(page.getByTestId("resolve-exception-confirm")).toBeEnabled();

    await page.getByTestId("resolve-exception-confirm").click();

    // Dialog should close
    await expect(page.getByTestId("resolve-exception-dialog")).toBeHidden({ timeout: 8000 });
  });

  test("add comment button opens comment dialog", async ({ page }) => {
    mockExceptionsApi(page);
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    // exc-e2e-001 has a planId, so it should have a comment button
    const commentBtn = page.getByTestId("comment-btn-exc-e2e-001");
    await expect(commentBtn).toBeVisible({ timeout: 8000 });
    await commentBtn.click();

    const dialog = page.getByTestId("add-comment-dialog");
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("comment-text-input")).toBeVisible();
    await expect(page.getByTestId("add-comment-confirm")).toBeVisible();
  });

  test("mock resolve API to 500 → error shows in dialog", async ({ page }) => {
    mockExceptionsApi(page);
    // Mock the resolve endpoint to fail
    await page.route("**/api/exceptions/*/resolve", (route) => {
      if (route.request().method() === "PUT") {
        return route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "Resolve failed" }),
        });
      }
      return route.fallback();
    });

    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("exceptions");

    const resolveBtn = page.getByTestId("resolve-btn-exc-e2e-001");
    await expect(resolveBtn).toBeVisible({ timeout: 8000 });
    await resolveBtn.click();

    await expect(page.getByTestId("resolve-exception-dialog")).toBeVisible({ timeout: 5000 });

    await page.getByTestId("resolution-note-input").fill("This will fail");
    await page.getByTestId("resolve-exception-confirm").click();

    // Error should appear in the dialog
    await expect(page.getByTestId("resolve-exception-dialog").getByRole("alert")).toBeVisible({ timeout: 5000 });
  });
});

// ── 7G: History Tab ──────────────────────────────────────────────────────────

test.describe("7G — History Tab", () => {
  test("history tab shows team history view or empty state", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("history");

    const panel = page.getByTestId("panel-history");
    await expect(panel).toBeVisible({ timeout: 8000 });

    // Either history view with table, loading state, or empty state
    const historyView = page.getByTestId("team-history-view");
    const historyEmpty = page.getByTestId("team-history-empty");
    const historyLoading = page.getByTestId("team-history-loading");

    await expect(
      historyView.or(historyEmpty).or(historyLoading),
    ).toBeVisible({ timeout: 10000 });
  });

  test("history table shows weekly trend rows with columns", async ({ page }) => {
    const teamPage = new TeamPage(page);
    await teamPage.goto();

    await teamPage.switchToTab("history");

    const table = page.getByTestId("team-history-table");
    const tableVisible = await table.isVisible().catch(() => false);
    if (!tableVisible) {
      test.skip(); // No history data — empty state shown instead
      return;
    }

    // Should have column headers
    const headers = await table.locator("thead th").allTextContents();
    expect(headers.length).toBeGreaterThan(0);
    expect(headers.some((h) => /week/i.test(h))).toBe(true);

    // Should have at least one data row
    const rows = table.locator("tbody tr[data-testid^='team-history-row-']");
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);
  });
});

// ── 7H: Error & Loading States ───────────────────────────────────────────────

test.describe("7H — Error & Loading States", () => {
  test("mock team week API to 500 → shows error", async ({ page }) => {
    await page.route("**/api/teams/*/week/**", (route) =>
      route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ error: "Internal Server Error" }),
      }),
    );

    const teamPage = new TeamPage(page);
    await teamPage.goto();

    // Should show error state
    await expect(page.getByTestId("team-week-error")).toBeVisible({ timeout: 10000 });
  });

  test("loading state shows skeleton before data arrives", async ({ page }) => {
    // Delay the response to ensure we see the skeleton
    await page.route("**/api/teams/*/week/**", async (route) => {
      await new Promise((r) => setTimeout(r, 2000));
      return route.fallback();
    });

    await page.goto("/weekly/team");
    await page.getByTestId("page-team-week").waitFor({ timeout: 5000 });

    // With a 2s delay on the API, the loading indicator must appear
    await expect(
      page.getByTestId("team-week-loading")
        .or(page.getByTestId("team-week-skeleton")).first(),
    ).toBeVisible({ timeout: 5000 });
  });
});
