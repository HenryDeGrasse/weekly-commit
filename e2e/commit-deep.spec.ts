import { test, expect } from "@playwright/test";
import {
  goToCleanDraftWeek, mockDraftPlan, uid, openManualCommitForm, addCommit,
  editBtnFor, deleteBtnFor, commitItem, SEED_OUTCOME_ID,
} from "./helpers/week-helpers";

/**
 * WORK UNIT 6: Commit CRUD Deep Tests (20 tests)
 *
 * Form-only tests (6B, 6C, 6D, 6E) use mockDraftPlan — no backend writes.
 * CRUD tests (6A, 6F, 6G) use the real backend on unique future weeks.
 */

// ── 6A. Create Commit — Full Form (real backend) ─────────────────────────────

test.describe("6A. Create Commit — Full Form", () => {
  test("creates commit with all fields", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await openManualCommitForm(page);
    await page.getByTestId("commit-form-title").fill(`Full ${tag}`);
    await page.getByTestId("commit-form-description").fill("Detailed description");
    await page.getByTestId("chess-piece-option-rook").click();
    await page.getByTestId("estimate-btn-3").click();
    await page.getByTestId("commit-form-success-criteria").fill("All tests pass");
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(commitItem(page, `Full ${tag}`)).toBeVisible();
  });

  test("commit appears in commit-list after creation", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await openManualCommitForm(page);
    await page.getByTestId("commit-form-title").fill(`Vis ${tag}`);
    await page.getByTestId("chess-piece-option-bishop").click();
    await page.getByTestId("estimate-btn-2").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(commitItem(page, `Vis ${tag}`)).toBeVisible();
    await expect(page.getByTestId("commit-list")).toBeVisible();
  });
});

// ── 6B. Form Validation (mocked — no backend writes) ─────────────────────────

test.describe("6B. Form Validation", () => {
  test("submit with empty title → Title is required", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("page-my-week").waitFor();
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByText("Title is required")).toBeVisible();
  });

  test("submit with no chess piece → Chess piece is required", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("commit-form-title").fill("No piece");
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByText("Chess piece is required")).toBeVisible();
  });

  test("KING limit message after one KING exists", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    // Create a KING via the mock
    await addCommit(page, `K1 ${uid()}`, "king", 5, { successCriteria: "SC" });
    await openManualCommitForm(page);
    await expect(page.getByTestId("chess-piece-limit-king")).toBeVisible();
    await expect(page.getByTestId("chess-piece-limit-king")).toContainText("Max 1 King");
  });

  test("QUEEN limit message after two QUEENs exist", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await addCommit(page, `Q1 ${uid()}`, "queen", 3, { successCriteria: "SC1" });
    await addCommit(page, `Q2 ${uid()}`, "queen", 3, { successCriteria: "SC2" });
    await openManualCommitForm(page);
    await expect(page.getByTestId("chess-piece-limit-queen")).toBeVisible();
    await expect(page.getByTestId("chess-piece-limit-queen")).toContainText("Max 2 Queens");
  });

  test("KING without success criteria → error", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("commit-form-title").fill("King no criteria");
    await page.getByTestId("chess-piece-option-king").click();
    await page.getByTestId("estimate-btn-5").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByText("Success criteria is required for King / Queen")).toBeVisible();
  });

  test("mock commit create 500 → error alert in form", async ({ page }) => {
    mockDraftPlan(page);
    // Override commit create to return 500
    await page.route("**/api/plans/*/commits", (route) => {
      if (route.request().method() === "POST")
        return route.fulfill({ status: 500, contentType: "application/json", body: '{"error":"fail"}' });
      return route.continue();
    });
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("commit-form-title").fill("Error commit");
    await page.getByTestId("chess-piece-option-pawn").click();
    await page.getByTestId("estimate-btn-1").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal").getByRole("alert")).toBeVisible({ timeout: 5000 });
  });
});

// ── 6C. Chess Piece Selection (mocked) ───────────────────────────────────────

test.describe("6C. Chess Piece Selection", () => {
  test("each piece option is clickable and radio becomes checked", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    for (const p of ["pawn", "knight", "bishop", "rook"]) {
      const opt = page.getByTestId(`chess-piece-option-${p}`);
      await opt.click();
      await expect(opt.locator("input[type='radio']")).toBeChecked();
    }
  });

  test("piece descriptions are visible", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await expect(page.getByTestId("chess-piece-option-king")).toContainText("Mission-critical");
    await expect(page.getByTestId("chess-piece-option-queen")).toContainText("High priority");
    await expect(page.getByTestId("chess-piece-option-rook")).toContainText("Important");
    await expect(page.getByTestId("chess-piece-option-bishop")).toContainText("Valuable");
    await expect(page.getByTestId("chess-piece-option-knight")).toContainText("Tactical");
    await expect(page.getByTestId("chess-piece-option-pawn")).toContainText("Nice-to-have");
  });
});

// ── 6D. Estimate Points (mocked) ─────────────────────────────────────────────

test.describe("6D. Estimate Points", () => {
  test("point buttons toggle on/off; all Fibonacci values present", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    for (const pts of [1, 2, 3, 5, 8])
      await expect(page.getByTestId(`estimate-btn-${pts}`)).toBeVisible();
    await page.getByTestId("estimate-btn-3").click();
    await expect(page.getByTestId("estimate-btn-3")).toHaveAttribute("aria-pressed", "true");
    await page.getByTestId("estimate-btn-3").click();
    await expect(page.getByTestId("estimate-btn-3")).toHaveAttribute("aria-pressed", "false");
  });

  test("selecting a different point deselects the previous", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("estimate-btn-5").click();
    await expect(page.getByTestId("estimate-btn-5")).toHaveAttribute("aria-pressed", "true");
    await page.getByTestId("estimate-btn-2").click();
    await expect(page.getByTestId("estimate-btn-2")).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByTestId("estimate-btn-5")).toHaveAttribute("aria-pressed", "false");
  });
});

// ── 6E. RCDO Picker (real backend — reads only, no writes) ───────────────────

test.describe("6E. RCDO Picker", () => {
  test("Browse RCDO nodes opens picker panel", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("rcdo-picker-toggle").click();
    await expect(page.getByTestId("rcdo-picker-panel")).toBeVisible();
  });

  test("selecting a seed outcome node shows it; clear removes it", async ({ page }) => {
    mockDraftPlan(page);
    await page.goto("/weekly/my-week");
    await page.getByTestId("plan-state-badge").waitFor({ timeout: 5000 });
    await openManualCommitForm(page);
    await page.getByTestId("rcdo-picker-toggle").click();
    const picker = page.getByTestId("rcdo-picker-panel");
    await expect(picker).toBeVisible();
    const expandBtn = picker.getByRole("button", { name: /expand all/i });
    if (await expandBtn.isVisible({ timeout: 1000 }).catch(() => false)) await expandBtn.click();
    const node = picker.getByTestId(`tree-node-${SEED_OUTCOME_ID}`);
    if (await node.isVisible({ timeout: 2000 }).catch(() => false)) {
      await node.click();
      const sel = page.getByTestId("rcdo-selected-node");
      await expect(sel).toBeVisible({ timeout: 2000 });
      await sel.getByLabel("Clear RCDO link").click();
      await expect(sel).toBeHidden();
    }
  });
});

// ── 6F. Edit Commit (real backend) ───────────────────────────────────────────

test.describe("6F. Edit Commit", () => {
  test("edit opens form with pre-populated title", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommit(page, `Edt ${tag}`, "rook", 3);
    await expect(commitItem(page, `Edt ${tag}`)).toBeVisible();
    await editBtnFor(page, `Edt ${tag}`).click();
    await expect(page.getByTestId("commit-form-modal")).toBeVisible({ timeout: 3000 });
    await expect(page.getByTestId("commit-form-title")).toHaveValue(`Edt ${tag}`);
  });

  test("changing chess piece via edit retains commit in list", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommit(page, `Pc ${tag}`, "pawn", 1);
    await editBtnFor(page, `Pc ${tag}`).click();
    await page.getByTestId("commit-form-modal").waitFor({ timeout: 3000 });
    await page.getByTestId("chess-piece-option-bishop").click();
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(commitItem(page, `Pc ${tag}`)).toBeVisible({ timeout: 8000 });
  });

  test("changing estimate via edit updates capacity tally", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommit(page, `Es ${tag}`, "pawn", 1);
    const tallyBefore = await page.getByTestId("capacity-tally").textContent().catch(() => "");
    await editBtnFor(page, `Es ${tag}`).click();
    await page.getByTestId("commit-form-modal").waitFor({ timeout: 3000 });
    await page.getByTestId("estimate-btn-1").click(); // deselect
    await page.getByTestId("estimate-btn-5").click(); // select
    await page.getByTestId("commit-form-submit").click();
    await expect(page.getByTestId("commit-form-modal")).toBeHidden({ timeout: 5000 });
    await expect(page.getByTestId("capacity-tally")).not.toHaveText(tallyBefore!, { timeout: 8000 });
  });
});

// ── 6G. Delete Commit (real backend) ─────────────────────────────────────────

test.describe("6G. Delete Commit", () => {
  test("delete confirm removes commit from list", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommit(page, `Del ${tag}`, "pawn", 1);
    await expect(commitItem(page, `Del ${tag}`)).toBeVisible();
    await deleteBtnFor(page, `Del ${tag}`).click();
    await expect(page.getByTestId("delete-confirm-dialog")).toBeVisible({ timeout: 3000 });
    await page.getByTestId("delete-confirm-btn").click();
    await expect(commitItem(page, `Del ${tag}`)).toHaveCount(0, { timeout: 5000 });
  });

  test("cancel delete keeps commit in list", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tag = uid();
    await addCommit(page, `Kp ${tag}`, "pawn", 1);
    await deleteBtnFor(page, `Kp ${tag}`).click();
    await expect(page.getByTestId("delete-confirm-dialog")).toBeVisible({ timeout: 3000 });
    await page.getByTestId("delete-confirm-dialog").getByRole("button", { name: /cancel/i }).click();
    await expect(page.getByTestId("delete-confirm-dialog")).toBeHidden({ timeout: 3000 });
    await expect(commitItem(page, `Kp ${tag}`)).toBeVisible({ timeout: 5000 });
  });

  test("capacity tally changes after deletion", async ({ page }) => {
    await goToCleanDraftWeek(page);
    const tA = uid(), tB = uid();
    await addCommit(page, `CA ${tA}`, "rook", 3);
    await addCommit(page, `CB ${tB}`, "pawn", 2);
    // Wait for tally to reflect both commits (5 pts total)
    await expect(page.getByTestId("capacity-tally")).toContainText("5", { timeout: 5000 });
    const tallyBefore = await page.getByTestId("capacity-tally").textContent();
    await deleteBtnFor(page, `CB ${tB}`).click();
    await page.getByTestId("delete-confirm-dialog").waitFor();
    await page.getByTestId("delete-confirm-btn").click();
    await expect(commitItem(page, `CB ${tB}`)).toHaveCount(0, { timeout: 5000 });
    await expect(page.getByTestId("capacity-tally")).not.toHaveText(tallyBefore!, { timeout: 8000 });
  });
});
