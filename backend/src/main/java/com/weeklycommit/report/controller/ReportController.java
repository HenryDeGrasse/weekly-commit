package com.weeklycommit.report.controller;

import com.weeklycommit.report.dto.AiAcceptanceReportEntry;
import com.weeklycommit.report.dto.CarryForwardReportEntry;
import com.weeklycommit.report.dto.ChessDistributionReportEntry;
import com.weeklycommit.report.dto.ComplianceReportEntry;
import com.weeklycommit.report.dto.ExceptionAgingEntry;
import com.weeklycommit.report.dto.PlannedVsAchievedEntry;
import com.weeklycommit.report.dto.RcdoCoverageReportEntry;
import com.weeklycommit.report.dto.ScopeChangeReportEntry;
import com.weeklycommit.report.service.ReportingService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for operational reporting from derived read-model tables.
 *
 * <p>
 * All report queries are backed by pre-computed derived tables and target a P95
 * response time of &lt; 1.5 seconds.
 *
 * <ul>
 * <li>{@code GET /api/reports/compliance} — lock/reconcile compliance</li>
 * <li>{@code GET /api/reports/planned-vs-achieved} — points comparison</li>
 * <li>{@code GET /api/reports/carry-forward} — carry-forward rates</li>
 * <li>{@code GET /api/reports/chess-distribution} — chess-piece
 * distribution</li>
 * <li>{@code GET /api/reports/scope-changes} — scope-change volume</li>
 * <li>{@code GET /api/reports/rcdo-coverage} — RCDO branch effort</li>
 * <li>{@code GET /api/reports/ai-acceptance} — AI suggestion acceptance</li>
 * <li>{@code GET /api/reports/exception-aging} — unresolved exception age</li>
 * </ul>
 */
@RestController
public class ReportController {

	private final ReportingService reportingService;

	public ReportController(ReportingService reportingService) {
		this.reportingService = reportingService;
	}

	// -------------------------------------------------------------------------
	// Compliance
	// -------------------------------------------------------------------------

	/**
	 * Returns lock and reconcile compliance per user per week for a team over a
	 * date range.
	 *
	 * @param teamId
	 *            required team filter
	 * @param weekStart
	 *            start of date range (inclusive, ISO date)
	 * @param weekEnd
	 *            end of date range (inclusive, ISO date)
	 */
	@GetMapping("/api/reports/compliance")
	public ResponseEntity<List<ComplianceReportEntry>> getComplianceReport(@RequestParam UUID teamId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd) {
		return ResponseEntity.ok(reportingService.getComplianceReport(teamId, weekStart, weekEnd));
	}

	// -------------------------------------------------------------------------
	// Planned vs achieved
	// -------------------------------------------------------------------------

	/**
	 * Returns planned vs achieved points per week for a team over a date range.
	 */
	@GetMapping("/api/reports/planned-vs-achieved")
	public ResponseEntity<List<PlannedVsAchievedEntry>> getPlannedVsAchieved(@RequestParam UUID teamId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd) {
		return ResponseEntity.ok(reportingService.getPlannedVsAchievedReport(teamId, weekStart, weekEnd));
	}

	// -------------------------------------------------------------------------
	// Carry-forward
	// -------------------------------------------------------------------------

	/**
	 * Returns carry-forward rates and counts per user per week for a team over a
	 * date range.
	 */
	@GetMapping("/api/reports/carry-forward")
	public ResponseEntity<List<CarryForwardReportEntry>> getCarryForwardReport(@RequestParam UUID teamId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd) {
		return ResponseEntity.ok(reportingService.getCarryForwardReport(teamId, weekStart, weekEnd));
	}

	// -------------------------------------------------------------------------
	// Chess distribution
	// -------------------------------------------------------------------------

	/**
	 * Returns chess-piece commit distribution for a team for a specific week.
	 */
	@GetMapping("/api/reports/chess-distribution")
	public ResponseEntity<ChessDistributionReportEntry> getChessDistribution(@RequestParam UUID teamId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		return ResponseEntity.ok(reportingService.getChessDistributionReport(teamId, weekStart));
	}

	// -------------------------------------------------------------------------
	// Scope changes
	// -------------------------------------------------------------------------

	/**
	 * Returns scope-change volume per user per week for a team over a date range.
	 */
	@GetMapping("/api/reports/scope-changes")
	public ResponseEntity<List<ScopeChangeReportEntry>> getScopeChanges(@RequestParam UUID teamId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd) {
		return ResponseEntity.ok(reportingService.getScopeChangeReport(teamId, weekStart, weekEnd));
	}

	// -------------------------------------------------------------------------
	// RCDO coverage
	// -------------------------------------------------------------------------

	/**
	 * Returns planned vs achieved effort for a specific RCDO node over a date
	 * range.
	 *
	 * @param rcdoNodeId
	 *            the RCDO node to report on
	 */
	@GetMapping("/api/reports/rcdo-coverage")
	public ResponseEntity<List<RcdoCoverageReportEntry>> getRcdoCoverage(@RequestParam UUID rcdoNodeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd) {
		return ResponseEntity.ok(reportingService.getRcdoCoverageReport(rcdoNodeId, weekStart, weekEnd));
	}

	// -------------------------------------------------------------------------
	// AI acceptance
	// -------------------------------------------------------------------------

	/**
	 * Returns aggregate AI suggestion acceptance rates across all time.
	 */
	@GetMapping("/api/reports/ai-acceptance")
	public ResponseEntity<AiAcceptanceReportEntry> getAiAcceptance() {
		return ResponseEntity.ok(reportingService.getAiAcceptanceReport());
	}

	// -------------------------------------------------------------------------
	// Exception aging
	// -------------------------------------------------------------------------

	/**
	 * Returns all unresolved manager exceptions for a team with their age in hours.
	 */
	@GetMapping("/api/reports/exception-aging")
	public ResponseEntity<List<ExceptionAgingEntry>> getExceptionAging(@RequestParam UUID teamId) {
		return ResponseEntity.ok(reportingService.getExceptionAgingReport(teamId));
	}
}
