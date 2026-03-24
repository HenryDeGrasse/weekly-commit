package com.weeklycommit.team.dto;

import java.util.UUID;

/**
 * Aggregate points and commit count for a single RCDO node across the team
 * week.
 */
public record RcdoRollupEntry(UUID rcdoNodeId, int commitCount, int totalPoints) {
}
