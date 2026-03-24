package com.weeklycommit.team.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated team view for a specific week, returned from {@code GET
 * /api/teams/{id}/week/{weekStart}}.
 *
 * <p>
 * Manager callers receive full {@link MemberWeekView} detail; peer callers
 * receive {@link PeerMemberWeekView} instances with sensitive fields stripped.
 */
public record TeamWeekViewResponse(UUID teamId, String teamName, LocalDate weekStart, List<MemberWeekView> memberViews,
		List<PeerMemberWeekView> peerViews, List<UncommittedTicketSummary> uncommittedAssignedTickets,
		List<UncommittedTicketSummary> uncommittedUnassignedTickets, List<RcdoRollupEntry> rcdoRollup,
		List<ChessDistributionEntry> chessDistribution, List<MemberComplianceSummary> complianceSummary) {
}
