import type { CommitResponse, LockValidationError } from "../../api/planTypes.js";

const VALID_ESTIMATE_POINTS = new Set([1, 2, 3, 5, 8]);
const MAX_KINGS = 1;
const MAX_QUEENS = 2;

export function derivePreLockHardErrors(
  commits: CommitResponse[],
): LockValidationError[] {
  const errors: LockValidationError[] = [];

  if (commits.length === 0) {
    return [
      {
        field: "commits",
        message: "At least one commit is required to lock the plan",
      },
    ];
  }

  let kingCount = 0;
  let queenCount = 0;

  for (const commit of commits) {
    const prefix = `commit[${commit.id}]`;

    if (!commit.title?.trim()) {
      errors.push({ field: `${prefix}.title`, message: "Title is required" });
    }

    if (!commit.chessPiece) {
      errors.push({
        field: `${prefix}.chessPiece`,
        message: "Chess piece is required",
      });
      continue;
    }

    if (commit.priorityOrder < 1) {
      errors.push({
        field: `${prefix}.priorityOrder`,
        message: "Priority order must be ≥ 1",
      });
    }

    if (!commit.rcdoNodeId) {
      errors.push({
        field: `${prefix}.rcdoNodeId`,
        message: "Primary RCDO link is required at lock time",
      });
    }

    if (commit.estimatePoints == null) {
      errors.push({
        field: `${prefix}.estimatePoints`,
        message: "Estimate points are required at lock time",
      });
    } else if (!VALID_ESTIMATE_POINTS.has(commit.estimatePoints)) {
      errors.push({
        field: `${prefix}.estimatePoints`,
        message: "Estimate points must be one of {1, 2, 3, 5, 8}",
      });
    }

    if (
      (commit.chessPiece === "KING" || commit.chessPiece === "QUEEN") &&
      !commit.successCriteria?.trim()
    ) {
      errors.push({
        field: `${prefix}.successCriteria`,
        message: `Success criteria required for ${commit.chessPiece} commits`,
      });
    }

    if (commit.chessPiece === "KING") kingCount += 1;
    if (commit.chessPiece === "QUEEN") queenCount += 1;
  }

  if (kingCount > MAX_KINGS) {
    errors.push({
      field: "commits.king",
      message: `Maximum 1 King commit per week; found ${kingCount}`,
    });
  }

  if (queenCount > MAX_QUEENS) {
    errors.push({
      field: "commits.queen",
      message: `Maximum 2 Queen commits per week; found ${queenCount}`,
    });
  }

  return errors;
}

export function derivePreLockSoftWarnings(commits: CommitResponse[]): string[] {
  const warnings: string[] = [];

  if (commits.length > 8) {
    warnings.push(
      `You have ${commits.length} commits — more than 8 can be hard to deliver consistently.`,
    );
  }

  const totalPts = commits.reduce((sum, commit) => sum + (commit.estimatePoints ?? 0), 0);
  const pawnPts = commits
    .filter((commit) => commit.chessPiece === "PAWN")
    .reduce((sum, commit) => sum + (commit.estimatePoints ?? 0), 0);

  if (totalPts > 0 && pawnPts / totalPts > 0.4) {
    const pct = Math.round((pawnPts / totalPts) * 100);
    warnings.push(
      `${pct}% of your points are Pawn commits — consider raising priority on key items.`,
    );
  }

  return warnings;
}

export function getEffectivePreLockErrors(
  commits: CommitResponse[],
  serverErrors: LockValidationError[],
): LockValidationError[] {
  const seen = new Set<string>();
  const combined = [...derivePreLockHardErrors(commits), ...serverErrors];

  return combined.filter((error) => {
    const key = `${error.field}:${error.message}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}
