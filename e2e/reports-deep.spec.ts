import { test, expect } from "@playwright/test";
import { ReportsPage } from "./pages/ReportsPage";

/**
 * E2E: Reports page — deep tests with mocked report API data.
 *
 * All report API endpoints are mocked via page.route() to provide
 * controlled data, since reports depend on having multiple reconciled weeks.
 *
 * Prerequisites:
 *   1. Frontend running at http://localhost:5173
 *   2. Backend running (for auth/plan APIs — reports are mocked)
 */

// ── Mock data templates ──────────────────────────────────────────────────

function weeksAgo(n: number): string {
  const d = new Date();
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff - n * 7);
  return d.toISOString().slice(0, 10);
}

function generatePvaData(weeks: number) {
  const data = [];
  for (let i = weeks - 1; i >= 0; i--) {
    data.push({
      teamId: "team-1",
      weekStart: weeksAgo(i),
      totalPlannedPoints: 10 + Math.floor(Math.random() * 5),
      totalAchievedPoints: 8 + Math.floor(Math.random() * 5),
      memberCount: 5,
      reconciledCount: 4,
    });
  }
  return data;
}

function generateComplianceData(weeks: number) {
  const data = [];
  for (let i = weeks - 1; i >= 0; i--) {
    for (let u = 1; u <= 3; u++) {
      data.push({
        userId: `user-${u}`,
        weekStart: weeksAgo(i),
        lockOnTime: Math.random() > 0.2,
        lockLate: Math.random() > 0.8,
        autoLocked: Math.random() > 0.9,
        reconcileOnTime: Math.random() > 0.3,
        reconcileLate: Math.random() > 0.7,
        reconcileMissed: Math.random() > 0.9,
      });
    }
  }
  return data;
}

function generateCfData(weeks: number) {
  const data = [];
  for (let i = weeks - 1; i >= 0; i--) {
    data.push({
      userId: "user-1",
      weekStart: weeksAgo(i),
      commitCount: 5,
      carryForwardCount: Math.floor(Math.random() * 2),
      carryForwardRate: 0.2,
    });
  }
  return data;
}

function generateScopeChangeData(weeks: number) {
  const data = [];
  for (let i = weeks - 1; i >= 0; i--) {
    data.push({
      userId: "user-1",
      weekStart: weeksAgo(i),
      scopeChangeCount: Math.floor(Math.random() * 5),
    });
  }
  return data;
}

function generateChessDistData(weekStart: string) {
  return {
    teamId: "team-1",
    weekStart,
    distribution: { KING: 1, QUEEN: 2, ROOK: 3, BISHOP: 2, KNIGHT: 1, PAWN: 4 },
  };
}

const MOCK_AI_ACCEPTANCE = {
  totalSuggestions: 100,
  totalFeedbackGiven: 60,
  acceptedCount: 30,
  dismissedCount: 30,
  acceptanceRate: 0.30,
};

const MOCK_EXCEPTION_AGING = [
  {
    exceptionId: "exc-1",
    teamId: "team-1",
    userId: "user-1",
    exceptionType: "MISSED_LOCK",
    severity: "HIGH",
    weekStartDate: weeksAgo(1),
    createdAt: new Date(Date.now() - 96 * 3600000).toISOString(),
    ageInHours: 96,
  },
  {
    exceptionId: "exc-2",
    teamId: "team-1",
    userId: "user-2",
    exceptionType: "OVER_BUDGET",
    severity: "MEDIUM",
    weekStartDate: weeksAgo(0),
    createdAt: new Date(Date.now() - 30 * 3600000).toISOString(),
    ageInHours: 30,
  },
  {
    exceptionId: "exc-3",
    teamId: "team-1",
    userId: "user-3",
    exceptionType: "AUTO_LOCKED",
    severity: "LOW",
    weekStartDate: weeksAgo(0),
    createdAt: new Date(Date.now() - 6 * 3600000).toISOString(),
    ageInHours: 6,
  },
];

/**
 * Mock all report API endpoints with realistic data for a given week range.
 */
function mockReportEndpoints(page: import("@playwright/test").Page, weeks = 8) {
  const pvaData = generatePvaData(weeks);
  const complianceData = generateComplianceData(weeks);
  const cfData = generateCfData(weeks);
  const scopeData = generateScopeChangeData(weeks);

  void page.route("**/api/reports/planned-vs-achieved**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(pvaData) }),
  );

  void page.route("**/api/reports/carry-forward**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(cfData) }),
  );

  void page.route("**/api/reports/compliance**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(complianceData) }),
  );

  void page.route("**/api/reports/chess-distribution**", (route) => {
    const url = new URL(route.request().url());
    const weekStart = url.searchParams.get("weekStart") ?? weeksAgo(0);
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(generateChessDistData(weekStart)),
    });
  });

  void page.route("**/api/reports/scope-changes**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(scopeData) }),
  );

  void page.route("**/api/reports/ai-acceptance**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_AI_ACCEPTANCE) }),
  );

  void page.route("**/api/reports/exception-aging**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_EXCEPTION_AGING) }),
  );
}

function mockReportEndpointsEmpty(page: import("@playwright/test").Page) {
  void page.route("**/api/reports/planned-vs-achieved**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
  void page.route("**/api/reports/carry-forward**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
  void page.route("**/api/reports/compliance**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
  void page.route("**/api/reports/chess-distribution**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ teamId: "team-1", weekStart: weeksAgo(0), distribution: {} }) }),
  );
  void page.route("**/api/reports/scope-changes**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
  void page.route("**/api/reports/ai-acceptance**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "null" }),
  );
  void page.route("**/api/reports/exception-aging**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
}

function mockReportEndpoints500(page: import("@playwright/test").Page) {
  const error500 = { status: 500, contentType: "application/json", body: JSON.stringify({ error: "Internal Server Error" }) };
  void page.route("**/api/reports/planned-vs-achieved**", (route) => route.fulfill(error500));
  void page.route("**/api/reports/carry-forward**", (route) => route.fulfill(error500));
  void page.route("**/api/reports/compliance**", (route) => route.fulfill(error500));
  void page.route("**/api/reports/chess-distribution**", (route) => route.fulfill(error500));
  void page.route("**/api/reports/scope-changes**", (route) => route.fulfill(error500));
  void page.route("**/api/reports/ai-acceptance**", (route) => route.fulfill(error500));
  void page.route("**/api/reports/exception-aging**", (route) => route.fulfill(error500));
}

// ── 5A: Page Structure & Controls ───────────────────────────────────────

test.describe("Reports — Page Structure & Controls", () => {
  test("page renders with heading 'Reports & Analytics'", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.pageContainer).toBeVisible();
    await expect(reports.pageContainer).toContainText("Reports & Analytics");
  });

  test("week range badge shows default '8 weeks'", async ({ page }) => {
    mockReportEndpoints(page, 8);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.pageContainer.getByText("8 weeks")).toBeVisible({ timeout: 5000 });
  });

  test("click 'More weeks' increases range to 12 weeks", async ({ page }) => {
    mockReportEndpoints(page, 12);
    const reports = new ReportsPage(page);
    await reports.goto();

    // Wait for initial load
    await expect(reports.pageContainer.getByText("8 weeks")).toBeVisible({ timeout: 5000 });

    // Click "More weeks" button
    await reports.pageContainer.getByText("More weeks").click();
    await expect(reports.pageContainer.getByText("12 weeks")).toBeVisible({ timeout: 5000 });
  });

  test("click 'Fewer' decreases range to 4 weeks (minimum)", async ({ page }) => {
    mockReportEndpoints(page, 4);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.pageContainer.getByText("8 weeks")).toBeVisible({ timeout: 5000 });

    // Click "Fewer" button
    await reports.pageContainer.getByText("Fewer").click();
    await expect(reports.pageContainer.getByText("4 weeks")).toBeVisible({ timeout: 5000 });
  });

  test("loading state shows skeleton components", async ({ page }) => {
    // Don't fulfill the routes immediately — let loading state show
    void page.route("**/api/reports/**", (route) => {
      // Delay response significantly so we can see the skeleton
      return new Promise((resolve) => {
        setTimeout(() => {
          resolve(route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
        }, 3000);
      });
    });

    await page.goto("/weekly/reports");
    await page.getByTestId("reports-page").waitFor();

    // Skeleton should be visible while loading
    await expect(page.getByTestId("report-chart-skeleton").first()).toBeVisible({ timeout: 3000 });
  });
});

// ── 5B: Empty State ─────────────────────────────────────────────────────

test.describe("Reports — Empty State", () => {
  test("shows empty state with 'Not enough data yet' when all APIs return empty", async ({ page }) => {
    mockReportEndpointsEmpty(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.reportsEmpty).toBeVisible({ timeout: 8000 });
    await expect(reports.reportsEmpty).toContainText("Not enough data yet");
  });
});

// ── 5C: Error State ─────────────────────────────────────────────────────

test.describe("Reports — Error State", () => {
  test("shows error with AlertTriangle when report APIs return 500", async ({ page }) => {
    mockReportEndpoints500(page);
    await page.goto("/weekly/reports");
    await page.getByTestId("reports-page").waitFor();

    // Error alert should be visible
    const errorAlert = page.getByRole("alert");
    await expect(errorAlert).toBeVisible({ timeout: 8000 });
  });
});

// ── 5D: Charts Render with Mock Data ────────────────────────────────────

test.describe("Reports — Charts Render with Mock Data", () => {
  test("velocity trend chart renders", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.velocityChart).toBeVisible({ timeout: 10000 });
    // Verify it contains chart content (Recharts renders SVG elements)
    await expect(reports.velocityChart).toContainText("Velocity");
  });

  test("planned vs achieved chart renders with grouped bars", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.pvaChart).toBeVisible({ timeout: 10000 });
    await expect(reports.pvaChart).toContainText("Planned vs. Achieved");
  });

  test("achievement rate chart renders with avg badge", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.achievementRateChart).toBeVisible({ timeout: 10000 });
    await expect(reports.achievementRateChart).toContainText("Achievement Rate");
    // Should show an avg badge
    await expect(reports.achievementRateChart.getByText(/avg \d+%/)).toBeVisible();
  });

  test("chess distribution chart renders with stacked bar and breakdown", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.chessDistChart).toBeVisible({ timeout: 10000 });
    await expect(reports.chessDistChart).toContainText("Chess Piece Distribution");
    // Should show piece names
    await expect(reports.chessDistChart.getByText("KING")).toBeVisible();
    await expect(reports.chessDistChart.getByText("PAWN")).toBeVisible();
  });

  test("scope change chart renders with bars", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.scopeChangeChart).toBeVisible({ timeout: 10000 });
    await expect(reports.scopeChangeChart).toContainText("Scope Changes");
  });

  test("carry-forward chart renders with CF Rate bars", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.cfChart).toBeVisible({ timeout: 10000 });
    await expect(reports.cfChart).toContainText("Carry-Forward");
  });

  test("compliance chart renders with lock/reconcile bars", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.complianceChart).toBeVisible({ timeout: 10000 });
    await expect(reports.complianceChart).toContainText("Compliance");
  });

  test("exception aging table renders with severity, type, week, age columns", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.exceptionAgingTable).toBeVisible({ timeout: 10000 });
    await expect(reports.exceptionAgingTable).toContainText("Exception Aging");
    // Table should have column headers
    await expect(reports.exceptionAgingTable.getByText("Severity")).toBeVisible();
    await expect(reports.exceptionAgingTable.getByText("Type")).toBeVisible();
    await expect(reports.exceptionAgingTable.getByText("Age")).toBeVisible();
  });

  test("AI acceptance card renders with total suggestions and rate bar", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.aiAcceptanceCard).toBeVisible({ timeout: 10000 });
    await expect(reports.aiAcceptanceCard).toContainText("AI Suggestion Acceptance");
    // Should show totals
    await expect(reports.aiAcceptanceCard.getByText("100")).toBeVisible();
    // Should show acceptance rate
    await expect(reports.aiAcceptanceCard.getByText("30%")).toBeVisible();
  });
});

// ── 5E: Chart Interactivity ─────────────────────────────────────────────

test.describe("Reports — Chart Interactivity", () => {
  test("velocity trend shows trend direction indicator based on last two weeks", async ({ page }) => {
    // Use fixed data where last week > second-to-last for a predictable TrendingUp
    const pvaFixed = [
      { teamId: "team-1", weekStart: weeksAgo(2), totalPlannedPoints: 10, totalAchievedPoints: 8, memberCount: 5, reconciledCount: 4 },
      { teamId: "team-1", weekStart: weeksAgo(1), totalPlannedPoints: 12, totalAchievedPoints: 7, memberCount: 5, reconciledCount: 4 },
      { teamId: "team-1", weekStart: weeksAgo(0), totalPlannedPoints: 14, totalAchievedPoints: 12, memberCount: 5, reconciledCount: 4 },
    ];

    void page.route("**/api/reports/planned-vs-achieved**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(pvaFixed) }),
    );
    // Mock other endpoints with standard data
    void page.route("**/api/reports/carry-forward**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateCfData(3)) }),
    );
    void page.route("**/api/reports/compliance**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateComplianceData(3)) }),
    );
    void page.route("**/api/reports/chess-distribution**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateChessDistData(weeksAgo(0))) }),
    );
    void page.route("**/api/reports/scope-changes**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateScopeChangeData(3)) }),
    );
    void page.route("**/api/reports/ai-acceptance**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_AI_ACCEPTANCE) }),
    );
    void page.route("**/api/reports/exception-aging**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_EXCEPTION_AGING) }),
    );

    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.velocityChart).toBeVisible({ timeout: 10000 });
    // trend = 12 - 7 = +5, so TrendingUp indicator should show "+5 pts"
    await expect(reports.velocityChart.getByText("+5 pts")).toBeVisible();
  });

  test("exception aging table: items show STALE/AGING/NEW based on age", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.exceptionAgingTable).toBeVisible({ timeout: 10000 });

    // exc-1: ageInHours=96 ≥ 72 → STALE
    await expect(reports.exceptionAgingTable.getByText("STALE", { exact: true })).toBeVisible();
    // exc-2: ageInHours=30 ≥ 24 → AGING
    await expect(reports.exceptionAgingTable.getByText("AGING", { exact: true })).toBeVisible();
    // exc-3: ageInHours=6 < 24 → NEW
    await expect(reports.exceptionAgingTable.getByText("NEW", { exact: true })).toBeVisible();
  });

  test("AI acceptance card: rate ≥25% shows 'Above 25%' badge", async ({ page }) => {
    mockReportEndpoints(page);
    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.aiAcceptanceCard).toBeVisible({ timeout: 10000 });
    // MOCK_AI_ACCEPTANCE has acceptanceRate 0.30 → 30% ≥ 25%
    await expect(reports.aiAcceptanceCard.getByText("Above 25%")).toBeVisible();
  });

  test("AI acceptance card: rate <25% shows 'Below 25%' badge", async ({ page }) => {
    const lowAcceptance = {
      totalSuggestions: 100,
      totalFeedbackGiven: 40,
      acceptedCount: 10,
      dismissedCount: 30,
      acceptanceRate: 0.10,
    };

    void page.route("**/api/reports/ai-acceptance**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(lowAcceptance) }),
    );
    // Use standard data for other endpoints
    void page.route("**/api/reports/planned-vs-achieved**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generatePvaData(8)) }),
    );
    void page.route("**/api/reports/carry-forward**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateCfData(8)) }),
    );
    void page.route("**/api/reports/compliance**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateComplianceData(8)) }),
    );
    void page.route("**/api/reports/chess-distribution**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateChessDistData(weeksAgo(0))) }),
    );
    void page.route("**/api/reports/scope-changes**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(generateScopeChangeData(8)) }),
    );
    void page.route("**/api/reports/exception-aging**", (route) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_EXCEPTION_AGING) }),
    );

    const reports = new ReportsPage(page);
    await reports.goto();

    await expect(reports.aiAcceptanceCard).toBeVisible({ timeout: 10000 });
    await expect(reports.aiAcceptanceCard.getByText("Below 25%")).toBeVisible();
  });
});
