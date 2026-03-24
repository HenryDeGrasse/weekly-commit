package com.weeklycommit.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.TeamWeekRollup;
import com.weeklycommit.domain.repository.AiFeedbackRepository;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.ComplianceFactRepository;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.RcdoWeekRollupRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamWeekRollupRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.report.dto.AiAcceptanceReportEntry;
import com.weeklycommit.report.dto.CarryForwardReportEntry;
import com.weeklycommit.report.dto.ChessDistributionReportEntry;
import com.weeklycommit.report.dto.ComplianceReportEntry;
import com.weeklycommit.report.dto.ExceptionAgingEntry;
import com.weeklycommit.report.dto.PlannedVsAchievedEntry;
import com.weeklycommit.report.dto.RcdoCoverageReportEntry;
import com.weeklycommit.report.dto.ScopeChangeReportEntry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting service that queries derived read-model tables for all report
 * endpoints. Reads are against pre-computed tables to meet P95 &lt; 1.5 s
 * target.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

	private final ComplianceFactRepository complianceFactRepo;
	private final TeamWeekRollupRepository teamWeekRollupRepo;
	private final UserWeekFactRepository userWeekFactRepo;
	private final RcdoWeekRollupRepository rcdoWeekRollupRepo;
	private final TeamMembershipRepository membershipRepo;
	private final ManagerReviewExceptionRepository exceptionRepo;
	private final AiFeedbackRepository feedbackRepo;
	private final AiSuggestionRepository suggestionRepo;
	private final ObjectMapper objectMapper;

	public ReportingService(ComplianceFactRepository complianceFactRepo, TeamWeekRollupRepository teamWeekRollupRepo,
			UserWeekFactRepository userWeekFactRepo, RcdoWeekRollupRepository rcdoWeekRollupRepo,
			TeamMembershipRepository membershipRepo, ManagerReviewExceptionRepository exceptionRepo,
			AiFeedbackRepository feedbackRepo, AiSuggestionRepository suggestionRepo, ObjectMapper objectMapper) {
		this.complianceFactRepo = complianceFactRepo;
		this.teamWeekRollupRepo = teamWeekRollupRepo;
		this.userWeekFactRepo = userWeekFactRepo;
		this.rcdoWeekRollupRepo = rcdoWeekRollupRepo;
		this.membershipRepo = membershipRepo;
		this.exceptionRepo = exceptionRepo;
		this.feedbackRepo = feedbackRepo;
		this.suggestionRepo = suggestionRepo;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Compliance report
	// -------------------------------------------------------------------------

	/**
	 * Returns lock/reconcile compliance per user per week for a team over a date
	 * range.
	 */
	public List<ComplianceReportEntry> getComplianceReport(UUID teamId, LocalDate weekStart, LocalDate weekEnd) {
		List<UUID> memberIds = getMemberIds(teamId);
		if (memberIds.isEmpty())
			return List.of();

		return complianceFactRepo.findByUserIdInAndWeekStartBetween(memberIds, weekStart, weekEnd).stream()
				.map(f -> new ComplianceReportEntry(f.getUserId(), f.getWeekStart(), f.isLockOnTime(), f.isLockLate(),
						f.isAutoLocked(), f.isReconcileOnTime(), f.isReconcileLate(), f.isReconcileMissed()))
				.toList();
	}

	// -------------------------------------------------------------------------
	// Planned vs achieved
	// -------------------------------------------------------------------------

	/**
	 * Returns planned vs achieved points for a team over a date range, querying
	 * {@code team_week_rollup}.
	 */
	public List<PlannedVsAchievedEntry> getPlannedVsAchievedReport(UUID teamId, LocalDate weekStart,
			LocalDate weekEnd) {
		return teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(teamId, weekStart, weekEnd).stream()
				.map(r -> new PlannedVsAchievedEntry(r.getTeamId(), r.getWeekStart(), r.getTotalPlannedPoints(),
						r.getTotalAchievedPoints(), r.getMemberCount(), r.getReconciledCount()))
				.toList();
	}

	// -------------------------------------------------------------------------
	// Carry-forward rates and streaks
	// -------------------------------------------------------------------------

	/**
	 * Returns carry-forward rates per user per week for a team over a date range.
	 */
	public List<CarryForwardReportEntry> getCarryForwardReport(UUID teamId, LocalDate weekStart, LocalDate weekEnd) {
		List<UUID> memberIds = getMemberIds(teamId);
		if (memberIds.isEmpty())
			return List.of();

		return userWeekFactRepo.findByUserIdInAndWeekStartBetween(memberIds, weekStart, weekEnd).stream().map(f -> {
			double rate = f.getCommitCount() == 0 ? 0.0 : (double) f.getCarryForwardCount() / f.getCommitCount();
			return new CarryForwardReportEntry(f.getUserId(), f.getWeekStart(), f.getCommitCount(),
					f.getCarryForwardCount(), rate);
		}).toList();
	}

	// -------------------------------------------------------------------------
	// Chess distribution
	// -------------------------------------------------------------------------

	/**
	 * Returns chess-piece commit distribution for a team for a specific week,
	 * querying {@code team_week_rollup}.
	 */
	public ChessDistributionReportEntry getChessDistributionReport(UUID teamId, LocalDate weekStart) {
		TeamWeekRollup rollup = teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, weekStart).orElse(null);
		if (rollup == null) {
			return new ChessDistributionReportEntry(teamId, weekStart, Map.of());
		}
		Map<String, Integer> dist = parseJsonMap(rollup.getChessDistribution());
		return new ChessDistributionReportEntry(teamId, weekStart, dist);
	}

	// -------------------------------------------------------------------------
	// Scope changes
	// -------------------------------------------------------------------------

	/**
	 * Returns scope-change volumes per user per week for a team over a date range.
	 */
	public List<ScopeChangeReportEntry> getScopeChangeReport(UUID teamId, LocalDate weekStart, LocalDate weekEnd) {
		List<UUID> memberIds = getMemberIds(teamId);
		if (memberIds.isEmpty())
			return List.of();

		return userWeekFactRepo.findByUserIdInAndWeekStartBetween(memberIds, weekStart, weekEnd).stream()
				.map(f -> new ScopeChangeReportEntry(f.getUserId(), f.getWeekStart(), f.getScopeChangeCount()))
				.toList();
	}

	// -------------------------------------------------------------------------
	// RCDO coverage
	// -------------------------------------------------------------------------

	/**
	 * Returns RCDO coverage (planned vs achieved effort) for a specific RCDO node
	 * over a date range, querying {@code rcdo_week_rollup}.
	 */
	public List<RcdoCoverageReportEntry> getRcdoCoverageReport(UUID rcdoNodeId, LocalDate weekStart,
			LocalDate weekEnd) {
		return rcdoWeekRollupRepo.findByRcdoNodeIdAndWeekStartBetween(rcdoNodeId, weekStart, weekEnd).stream()
				.map(r -> {
					Map<String, Integer> breakdown = parseJsonMap(r.getTeamContributionBreakdown());
					return new RcdoCoverageReportEntry(r.getRcdoNodeId(), r.getWeekStart(), r.getPlannedPoints(),
							r.getAchievedPoints(), r.getCommitCount(), breakdown);
				}).toList();
	}

	// -------------------------------------------------------------------------
	// AI acceptance
	// -------------------------------------------------------------------------

	/**
	 * Returns aggregate AI suggestion acceptance rates. Queries transactional
	 * tables directly (small dataset, fast indexed query).
	 */
	public AiAcceptanceReportEntry getAiAcceptanceReport() {
		long totalSuggestions = suggestionRepo.count();
		long totalFeedback = feedbackRepo.count();
		long acceptedCount = feedbackRepo.countByAccepted(true);
		long dismissedCount = feedbackRepo.countByAccepted(false);
		double acceptanceRate = totalFeedback == 0 ? 0.0 : (double) acceptedCount / totalFeedback;

		return new AiAcceptanceReportEntry(totalSuggestions, totalFeedback, acceptedCount, dismissedCount,
				acceptanceRate);
	}

	// -------------------------------------------------------------------------
	// Exception aging
	// -------------------------------------------------------------------------

	/**
	 * Returns all unresolved manager exceptions for a team with their age. Queries
	 * the transactional table directly (exceptions are low-volume).
	 */
	public List<ExceptionAgingEntry> getExceptionAgingReport(UUID teamId) {
		Instant now = Instant.now();
		return exceptionRepo.findByTeamIdAndResolved(teamId, false).stream().map(e -> {
			long ageHours = e.getCreatedAt() != null ? Duration.between(e.getCreatedAt(), now).toHours() : 0L;
			return new ExceptionAgingEntry(e.getId(), e.getTeamId(), e.getUserId(), e.getExceptionType().name(),
					e.getSeverity().name(), e.getWeekStartDate(), e.getCreatedAt(), ageHours);
		}).toList();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private List<UUID> getMemberIds(UUID teamId) {
		return membershipRepo.findByTeamId(teamId).stream().map(m -> m.getUserId()).toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Integer> parseJsonMap(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
			});
		} catch (JsonProcessingException e) {
			return Collections.emptyMap();
		}
	}
}
