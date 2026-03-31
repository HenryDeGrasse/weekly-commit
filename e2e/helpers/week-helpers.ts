/**
 * Shared helpers for E2E tests.
 *
 * Two strategies to avoid flakiness from persistent backend state:
 *
 * 1. MOCK strategy — for form-only tests (validation, UI interactions):
 *    `mockDraftPlan(page)` intercepts `POST /api/plans` and returns a fake
 *    empty DRAFT plan. Commit creates are also intercepted. No backend writes.
 *
 * 2. REAL strategy — for tests that need real CRUD (create, edit, delete, lock):
 *    `goToCleanDraftWeek(page)` navigates to a unique far-future week using a
 *    run-unique timestamp. `cleanupCommits(page, planId, commitIds)` deletes
 *    created commits in afterEach.
 */
import { expect, type Page } from "@playwright/test";

// ── Well-known seed data ──────────────────────────────────────────────────────

export const SEED_OUTCOME_ID = "00000000-0000-0000-cccc-000000000001";
export const SEED_USER_ID = "00000000-0000-0000-0000-000000000001";

// ── Unique run ID (changes every process start) ──────────────────────────────

const RUN_TS = Date.now();
let weekSeq = 0;

// ── Week navigation ──────────────────────────────────────────────────────────

function mondayFromOffset(offsetWeeks: number): string {
  const now = new Date();
  const dow = now.getDay();
  const toMon = dow === 0 ? -6 : 1 - dow;
  const d = new Date(now);
  d.setDate(now.getDate() + toMon + offsetWeeks * 7);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * Navigate to a guaranteed-clean far-future DRAFT week.
 *
 * Uses a monotonically increasing per-run sequence so no two tests
 * (within the same run) ever share a week, and the run timestamp
 * separates runs.
 */
export async function goToCleanDraftWeek(page: Page) {
  await page.goto("/weekly/my-week");
  await page.getByTestId("page-my-week").waitFor();

  // Each call gets the next offset: 500, 501, 502, ...
  // Combined with RUN_TS-based base we get truly unique weeks per run.
  const base = 500 + ((RUN_TS % 200) * 10); // 500–2490 base, shifts each run
  const offset = base + weekSeq++;
  const iso = mondayFromOffset(offset);

  await page.locator("#week-selector-input").fill(iso);
  await page.locator("#week-selector-input").dispatchEvent("change");
  await page.getByTestId("plan-state-badge").waitFor({ timeout: 10_000 });

  // If we somehow hit a non-DRAFT (from a very old run), jump forward
  for (let retry = 0; retry < 3; retry++) {
    const state = await page.getByTestId("plan-state-badge").textContent();
    if (state?.includes("DRAFT")) {
      const dirty = await page.getByTestId("commit-list").isVisible({ timeout: 300 }).catch(() => false);
      if (!dirty) return; // Clean empty DRAFT ✓
    }
    // Jump +100 weeks
    const next = mondayFromOffset(offset + (retry + 1) * 100);
    await page.locator("#week-selector-input").fill(next);
    await page.locator("#week-selector-input").dispatchEvent("change");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 10_000 });
  }
  await expect(page.getByTestId("plan-state-badge")).toContainText("DRAFT");
}

// ── Mock strategy ─────────────────────────────────────────────────────────────

let mockCommitSeq = 0;

/**
 * Mock the plan API to return a fresh empty DRAFT plan and handle
 * commit creation/update client-side. Use for form-only tests.
 *
 * Returns the mock plan ID.
 */
export function mockDraftPlan(page: Page): string {
  const planId = `mock-plan-${Date.now()}`;
  const commits: Record<string, unknown>[] = [];

  // Intercept plan get-or-create
  void page.route("**/api/plans", (route) => {
    if (route.request().method() === "POST") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          plan: {
            id: planId, userId: SEED_USER_ID, weekStartDate: "2050-01-03",
            state: "DRAFT", compliant: true, systemLockedWithErrors: false,
            capacityBudgetPoints: 10,
            createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
          },
          commits,
        }),
      });
    }
    return route.continue();
  });

  // Intercept plan get by ID
  void page.route(`**/api/plans/${planId}`, (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          plan: {
            id: planId, userId: SEED_USER_ID, weekStartDate: "2050-01-03",
            state: "DRAFT", compliant: true, systemLockedWithErrors: false,
            capacityBudgetPoints: 10,
            createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
          },
          commits,
        }),
      });
    }
    return route.continue();
  });

  // Intercept commit create
  void page.route(`**/api/plans/${planId}/commits`, (route) => {
    if (route.request().method() === "POST") {
      const body = route.request().postDataJSON();
      const commit = {
        id: `mock-commit-${++mockCommitSeq}`,
        planId,
        title: body.title ?? "",
        chessPiece: body.chessPiece ?? "PAWN",
        estimatePoints: body.estimatePoints ?? null,
        description: body.description ?? null,
        successCriteria: body.successCriteria ?? null,
        rcdoNodeId: body.rcdoNodeId ?? null,
        workItemId: body.workItemId ?? null,
        priorityOrder: commits.length + 1,
        carryForwardStreak: 0,
        outcome: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      commits.push(commit);
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(commit) });
    }
    return route.continue();
  });

  return planId;
}

// ── Common UI helpers ─────────────────────────────────────────────────────────

export function uid(): string {
  return Math.random().toString(36).slice(2, 8);
}

export async function openManualCommitForm(page: Page) {
  const manualBtn = page.getByTestId("add-commit-manually-btn");
  if (await manualBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
    await manualBtn.click();
  } else {
    await page.getByTestId("add-commit-btn").click();
  }
  if (await page.getByTestId("ai-commit-composer").isVisible({ timeout: 500 }).catch(() => false)) {
    const sw = page.getByTestId("ai-composer-switch-manual-link")
      .or(page.getByTestId("ai-composer-switch-manual-btn"));
    await sw.first().click();
  }
  await page.getByTestId("commit-form-modal").waitFor({ timeout: 5000 });
}

export async function addCommit(
  page: Page, title: string, piece: string, pts: number,
  opts?: { successCriteria?: string },
) {
  await openManualCommitForm(page);
  await page.getByTestId("commit-form-title").fill(title);
  await page.getByTestId(`chess-piece-option-${piece}`).click();
  await page.getByTestId(`estimate-btn-${pts}`).click();
  if (opts?.successCriteria) {
    await page.getByTestId("commit-form-success-criteria").fill(opts.successCriteria);
  }
  await page.getByTestId("commit-form-submit").click();
  await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
}

export async function addCommitWithRcdo(page: Page, title: string, piece: string, pts: number) {
  await openManualCommitForm(page);
  await page.getByTestId("commit-form-title").fill(title);
  await page.getByTestId(`chess-piece-option-${piece}`).click();
  await page.getByTestId(`estimate-btn-${pts}`).click();

  await page.getByTestId("rcdo-picker-toggle").click();
  const picker = page.getByTestId("rcdo-picker-panel");
  await expect(picker).toBeVisible({ timeout: 3000 });
  const expandBtn = picker.getByRole("button", { name: /expand all/i });
  if (await expandBtn.isVisible({ timeout: 1000 }).catch(() => false)) await expandBtn.click();
  const node = picker.getByTestId(`tree-node-${SEED_OUTCOME_ID}`);
  await expect(node).toBeVisible({ timeout: 3000 });
  await node.click();
  await expect(page.getByTestId("rcdo-selected-node")).toBeVisible({ timeout: 2000 });

  await page.getByTestId("commit-form-submit").click();
  await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
}

export async function lockCurrentPlan(page: Page) {
  await page.getByTestId("lock-plan-btn").click();
  await page.getByTestId("pre-lock-validation-section").waitFor({ timeout: 5000 });
  await expect(page.getByTestId("pre-lock-continue-btn")).toBeVisible({ timeout: 5000 });
  await page.getByTestId("pre-lock-continue-btn").click();
  await expect(page.getByTestId("lock-confirm-dialog")).toBeVisible({ timeout: 5000 });
  await page.getByTestId("lock-confirm-btn").click();
  await expect(page.getByTestId("plan-state-badge")).toContainText("LOCKED", { timeout: 10_000 });
}

export function mockAiUnavailable(page: Page) {
  void page.route("**/api/ai/status", (route) =>
    route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ aiEnabled: false, providerName: "mock", providerVersion: "1.0", available: false }),
    }),
  );
}

export async function openPostLockForm(page: Page) {
  await page.getByTestId("post-lock-add-commit-btn").click();
  const either = page.getByTestId("ai-commit-composer").or(page.getByTestId("commit-form-modal"));
  await expect(either).toBeVisible({ timeout: 5000 });
  if (await page.getByTestId("ai-commit-composer").isVisible().catch(() => false)) {
    const sw = page.getByTestId("ai-composer-switch-manual-link")
      .or(page.getByTestId("ai-composer-switch-manual-btn"));
    await sw.first().click();
  }
  await expect(page.getByTestId("commit-form-modal")).toBeVisible({ timeout: 5000 });
}

/** Locator for the edit button of a commit identified by title text. */
export function editBtnFor(page: Page, titleText: string) {
  return page.locator("[data-testid^='commit-item-']")
    .filter({ hasText: titleText })
    .locator("[data-testid^='edit-commit-']");
}

/** Locator for the delete button of a commit identified by title text. */
export function deleteBtnFor(page: Page, titleText: string) {
  return page.locator("[data-testid^='commit-item-']")
    .filter({ hasText: titleText })
    .locator("[data-testid^='delete-commit-']");
}

/** Locator for a commit item by title text (scoped to commit-item, avoids AI lint text). */
export function commitItem(page: Page, titleText: string) {
  return page.locator("[data-testid^='commit-item-']").filter({ hasText: titleText });
}
