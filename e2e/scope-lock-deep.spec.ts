import { test, expect } from "@playwright/test";
import {
  goToCleanDraftWeek, uid, addCommit, addCommitWithRcdo,
  lockCurrentPlan, mockAiUnavailable, openPostLockForm, commitItem,
} from "./helpers/week-helpers";

/**
 * WORK UNIT 10: Scope Changes & Lock Validation Deep (18 tests)
 *
 * All tests use the real backend on unique far-future weeks.
 * Post-lock tests mock AI as unavailable so the post-lock add button
 * opens the manual CommitForm (bypassing the AI Composer).
 */

// ── 10A. Pre-Lock Validation ─────────────────────────────────────────────────

test.describe("10A. Pre-Lock Validation", () => {
  test("empty plan → lock shows hard errors requiring commits", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await page.getByTestId("lock-plan-btn").click();
    const section = page.getByTestId("pre-lock-validation-section");
    await expect(section).toBeVisible({ timeout: 5000 });
    expect(await page.getByTestId("hard-error-item").count()).toBeGreaterThan(0);
    await expect(section).toContainText("commit");
  });

  test("hard-error-item elements have non-empty text", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await page.getByTestId("lock-plan-btn").click();
    await page.getByTestId("pre-lock-validation-section").waitFor({ timeout: 5000 });
    const first = page.getByTestId("hard-error-item").first();
    await expect(first).toBeVisible();
    expect((await first.textContent())!.length).toBeGreaterThan(0);
  });

  test("commit without RCDO → validation shows RCDO required error", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await addCommit(page, `NoRCDO ${uid()}`, "pawn", 2);
    await page.getByTestId("lock-plan-btn").click();
    await page.getByTestId("pre-lock-validation-section").waitFor({ timeout: 5000 });
    await expect(page.getByTestId("pre-lock-validation-section")).toContainText("RCDO link is required");
  });

  test("commit with RCDO → continue button appears", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `WithRCDO ${uid()}`, "pawn", 2);
    await page.getByTestId("lock-plan-btn").click();
    await page.getByTestId("pre-lock-validation-section").waitFor({ timeout: 5000 });
    await expect(page.getByTestId("pre-lock-continue-btn")).toBeVisible({ timeout: 5000 });
  });

  test("auto-lock banner on system-locked plan (mocked)", async ({ page }) => {
    await page.route("**/api/plans", (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          status: 200, contentType: "application/json",
          body: JSON.stringify({
            plan: { id: "auto-lock", userId: "u1", weekStartDate: "2050-06-06", state: "LOCKED",
              compliant: false, systemLockedWithErrors: true, capacityBudgetPoints: 10,
              createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
            commits: [{ id: "c1", planId: "auto-lock", title: "X", chessPiece: "PAWN",
              estimatePoints: 2, priorityOrder: 1, rcdoNodeId: null, carryForwardStreak: 0 }],
          }),
        });
      }
      return route.continue();
    });
    await page.goto("/weekly/my-week");
    await page.getByTestId("page-my-week").waitFor();
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await expect(page.getByTestId("auto-lock-banner")).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("auto-lock-banner")).toContainText("System-locked");
  });
});

// ── 10B. Lock Confirmation Dialog ────────────────────────────────────────────

test.describe("10B. Lock Confirmation Dialog", () => {
  test("dialog shows commit count, total points, piece breakdown, RCDO coverage", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `Rk ${uid()}`, "rook", 3);
    await addCommitWithRcdo(page, `Pw ${uid()}`, "pawn", 2);
    await page.getByTestId("lock-plan-btn").click();
    await page.getByTestId("pre-lock-validation-section").waitFor({ timeout: 5000 });
    await page.getByTestId("pre-lock-continue-btn").click();
    await expect(page.getByTestId("lock-confirm-dialog")).toBeVisible({ timeout: 3000 });
    await expect(page.getByTestId("lock-confirm-commit-count")).toContainText("2");
    await expect(page.getByTestId("lock-confirm-total-points")).toContainText("5");
    await expect(page.getByTestId("lock-confirm-piece-breakdown")).toBeVisible();
    await expect(page.getByTestId("lock-confirm-rcdo-coverage")).toBeVisible();
  });

  test("cancel keeps plan in DRAFT", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `Cancel ${uid()}`, "pawn", 1);
    await page.getByTestId("lock-plan-btn").click();
    await page.getByTestId("pre-lock-validation-section").waitFor({ timeout: 5000 });
    await page.getByTestId("pre-lock-continue-btn").click();
    await expect(page.getByTestId("lock-confirm-dialog")).toBeVisible({ timeout: 3000 });
    await page.getByTestId("lock-confirm-cancel").click();
    await expect(page.getByTestId("lock-confirm-dialog")).toBeHidden({ timeout: 3000 });
    await expect(page.getByTestId("plan-state-badge")).toContainText("DRAFT");
  });

  test("confirm transitions plan to LOCKED", async ({ page }) => {
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `Lock ${uid()}`, "pawn", 2);
    await lockCurrentPlan(page);
    await expect(page.getByTestId("plan-state-badge")).toContainText("LOCKED");
  });
});

// ── 10C. Post-Lock Scope Change Dialog ───────────────────────────────────────

test.describe("10C. Post-Lock Scope Change Dialog", () => {
  test("post-lock add triggers scope change dialog with locked notice", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `Bsc ${uid()}`, "rook", 3);
    await lockCurrentPlan(page);
    await openPostLockForm(page);
    await page.getByTestId("commit-form-title").fill(`Post ${uid()}`);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-dialog")).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-locked-notice")).toBeVisible();
  });

  test("scope change reason select has multiple options", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `Rsn ${uid()}`, "pawn", 2);
    await lockCurrentPlan(page);
    await openPostLockForm(page);
    await page.getByTestId("commit-form-title").fill(`Rsn ${uid()}`);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-dialog")).toBeVisible({ timeout: 5000 });
    expect(await page.getByTestId("scope-change-reason-select").locator("option").count()).toBeGreaterThanOrEqual(3);
  });

  test("scope change confirm adds commit to the list", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommitWithRcdo(page, `Pre ${tag}`, "rook", 3);
    await lockCurrentPlan(page);
    await openPostLockForm(page);
    await page.getByTestId("commit-form-title").fill(`ScopeAdd ${tag}`);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-dialog")).toBeVisible({ timeout: 5000 });
    await page.getByTestId("scope-change-reason-select").selectOption({ index: 1 });
    await page.getByTestId("scope-change-confirm").click();
    await expect(page.getByTestId("scope-change-dialog")).toBeHidden({ timeout: 5000 });
    await expect(commitItem(page, `ScopeAdd ${tag}`)).toBeVisible({ timeout: 5000 });
  });

  test("scope change cancel closes dialog", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `Cnl ${uid()}`, "pawn", 2);
    await lockCurrentPlan(page);
    await openPostLockForm(page);
    await page.getByTestId("commit-form-title").fill(`CnlSc ${uid()}`);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-dialog")).toBeVisible({ timeout: 5000 });
    await page.getByTestId("scope-change-cancel").click();
    await expect(page.getByTestId("scope-change-dialog")).toBeHidden({ timeout: 3000 });
  });

  test("scope change add preview shows commit title", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommitWithRcdo(page, `Prv ${tag}`, "pawn", 2);
    await lockCurrentPlan(page);
    await openPostLockForm(page);
    await page.getByTestId("commit-form-title").fill(`Preview ${tag}`);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-dialog")).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-add-preview")).toContainText(`Preview ${tag}`);
  });
});

// ── 10D. Scope Change Timeline ───────────────────────────────────────────────

test.describe("10D. Scope Change Timeline", () => {
  test("scope timeline button visible on LOCKED plan", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `TL ${uid()}`, "pawn", 2);
    await lockCurrentPlan(page);
    const btn = page.getByTestId("load-scope-timeline-btn");
    await btn.scrollIntoViewIfNeeded();
    await expect(btn).toBeVisible({ timeout: 5000 });
  });

  test("clicking timeline button shows timeline or empty state", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    await addCommitWithRcdo(page, `TL2 ${uid()}`, "pawn", 2);
    await lockCurrentPlan(page);
    const btn = page.getByTestId("load-scope-timeline-btn");
    await btn.scrollIntoViewIfNeeded();
    await btn.click();
    await expect(
      page.getByTestId("scope-change-timeline").or(page.getByTestId("scope-change-timeline-empty")),
    ).toBeVisible({ timeout: 5000 });
  });

  test("timeline shows events after post-lock scope change", async ({ page }) => {
    mockAiUnavailable(page);
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommitWithRcdo(page, `TL3 ${tag}`, "rook", 3);
    await lockCurrentPlan(page);
    await openPostLockForm(page);
    await page.getByTestId("commit-form-title").fill(`ScEv ${tag}`);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-dialog")).toBeVisible({ timeout: 5000 });
    await page.getByTestId("scope-change-reason-select").selectOption({ index: 1 });
    await page.getByTestId("scope-change-confirm").click();
    await expect(page.getByTestId("scope-change-dialog")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("scope-change-timeline")).toBeVisible({ timeout: 5000 });
  });
});

// ── 10E. Soft Warnings Panel ─────────────────────────────────────────────────

test.describe("10E. Soft Warnings Panel", () => {
  test("too many commits warning when >8 active commits", async ({ page }) => {
    await goToCleanDraftWeek(page);
    for (let i = 1; i <= 9; i++) await addCommit(page, `PO${i} ${uid()}`, "pawn", 1);
    await expect(page.getByTestId("soft-warnings-panel")).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("warning-too-many-commits")).toContainText("Too many commits");
  });

  test("pawn-heavy warning when >40% of points are pawns", async ({ page }) => {
    await goToCleanDraftWeek(page);
    for (let i = 1; i <= 5; i++) await addCommit(page, `PH${i} ${uid()}`, "pawn", 1);
    await addCommit(page, `PHRk ${uid()}`, "rook", 2);
    await expect(page.getByTestId("soft-warnings-panel")).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("warning-pawn-heavy")).toContainText("Pawn-heavy");
  });
});
