package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Derived read-model: per-team per-week rollup. Refreshed on lifecycle events
 * and on a 5-minute scheduled cadence.
 *
 * <p>
 * {@code chessDistribution} stores a JSON object mapping chess-piece name to
 * count, e.g. {@code {"KING":1,"QUEEN":2,"ROOK":3}}.
 */
@Entity
@Table(name = "team_week_rollup")
public class TeamWeekRollup {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	@Column(name = "member_count", nullable = false)
	private int memberCount;

	@Column(name = "locked_count", nullable = false)
	private int lockedCount;

	@Column(name = "reconciled_count", nullable = false)
	private int reconciledCount;

	@Column(name = "total_planned_points", nullable = false)
	private int totalPlannedPoints;

	@Column(name = "total_achieved_points", nullable = false)
	private int totalAchievedPoints;

	@Column(name = "exception_count", nullable = false)
	private int exceptionCount;

	@Column(name = "avg_carry_forward_rate", nullable = false)
	private double avgCarryForwardRate;

	/** JSON string — map of chess-piece name → count. */
	@Column(name = "chess_distribution", columnDefinition = "jsonb")
	private String chessDistribution;

	@Column(name = "refreshed_at", nullable = false)
	private Instant refreshedAt = Instant.now();

	// ---- Getters / Setters -----------------------------------------------

	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getTeamId() {
		return teamId;
	}
	public void setTeamId(UUID teamId) {
		this.teamId = teamId;
	}

	public LocalDate getWeekStart() {
		return weekStart;
	}
	public void setWeekStart(LocalDate weekStart) {
		this.weekStart = weekStart;
	}

	public int getMemberCount() {
		return memberCount;
	}
	public void setMemberCount(int memberCount) {
		this.memberCount = memberCount;
	}

	public int getLockedCount() {
		return lockedCount;
	}
	public void setLockedCount(int lockedCount) {
		this.lockedCount = lockedCount;
	}

	public int getReconciledCount() {
		return reconciledCount;
	}
	public void setReconciledCount(int reconciledCount) {
		this.reconciledCount = reconciledCount;
	}

	public int getTotalPlannedPoints() {
		return totalPlannedPoints;
	}
	public void setTotalPlannedPoints(int totalPlannedPoints) {
		this.totalPlannedPoints = totalPlannedPoints;
	}

	public int getTotalAchievedPoints() {
		return totalAchievedPoints;
	}
	public void setTotalAchievedPoints(int totalAchievedPoints) {
		this.totalAchievedPoints = totalAchievedPoints;
	}

	public int getExceptionCount() {
		return exceptionCount;
	}
	public void setExceptionCount(int exceptionCount) {
		this.exceptionCount = exceptionCount;
	}

	public double getAvgCarryForwardRate() {
		return avgCarryForwardRate;
	}
	public void setAvgCarryForwardRate(double avgCarryForwardRate) {
		this.avgCarryForwardRate = avgCarryForwardRate;
	}

	public String getChessDistribution() {
		return chessDistribution;
	}
	public void setChessDistribution(String chessDistribution) {
		this.chessDistribution = chessDistribution;
	}

	public Instant getRefreshedAt() {
		return refreshedAt;
	}
	public void setRefreshedAt(Instant refreshedAt) {
		this.refreshedAt = refreshedAt;
	}
}
