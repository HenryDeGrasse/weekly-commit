package com.weeklycommit.plan.dto;

import java.util.List;

/**
 * Plan header together with its commits in priority order and total point sum.
 */
public record PlanWithCommitsResponse(PlanResponse plan, List<CommitResponse> commits, int totalPoints) {
}
