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
 * Derived read-model: per-RCDO-node per-week rollup.
 *
 * <p>
 * {@code teamContributionBreakdown} stores a JSON object mapping teamId
 * (string) to planned-points contribution, e.g.
 * {@code {"<uuid>":5,"<uuid>":3}}.
 */
@Entity
@Table(name = "rcdo_week_rollup")
public class RcdoWeekRollup {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "rcdo_node_id", nullable = false)
	private UUID rcdoNodeId;

	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	@Column(name = "planned_points", nullable = false)
	private int plannedPoints;

	@Column(name = "achieved_points", nullable = false)
	private int achievedPoints;

	@Column(name = "commit_count", nullable = false)
	private int commitCount;

	/** JSON string — map of teamId → points. */
	@Column(name = "team_contribution_breakdown", columnDefinition = "jsonb")
	private String teamContributionBreakdown;

	@Column(name = "refreshed_at", nullable = false)
	private Instant refreshedAt = Instant.now();

	// ---- Getters / Setters -----------------------------------------------

	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getRcdoNodeId() {
		return rcdoNodeId;
	}
	public void setRcdoNodeId(UUID rcdoNodeId) {
		this.rcdoNodeId = rcdoNodeId;
	}

	public LocalDate getWeekStart() {
		return weekStart;
	}
	public void setWeekStart(LocalDate weekStart) {
		this.weekStart = weekStart;
	}

	public int getPlannedPoints() {
		return plannedPoints;
	}
	public void setPlannedPoints(int plannedPoints) {
		this.plannedPoints = plannedPoints;
	}

	public int getAchievedPoints() {
		return achievedPoints;
	}
	public void setAchievedPoints(int achievedPoints) {
		this.achievedPoints = achievedPoints;
	}

	public int getCommitCount() {
		return commitCount;
	}
	public void setCommitCount(int commitCount) {
		this.commitCount = commitCount;
	}

	public String getTeamContributionBreakdown() {
		return teamContributionBreakdown;
	}
	public void setTeamContributionBreakdown(String teamContributionBreakdown) {
		this.teamContributionBreakdown = teamContributionBreakdown;
	}

	public Instant getRefreshedAt() {
		return refreshedAt;
	}
	public void setRefreshedAt(Instant refreshedAt) {
		this.refreshedAt = refreshedAt;
	}
}
