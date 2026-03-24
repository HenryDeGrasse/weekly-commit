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
 * Derived read-model: per-user per-week aggregate fact table. Refreshed by
 * {@code ReadModelRefreshService} on lifecycle events and on a 5-minute
 * scheduled cadence.
 */
@Entity
@Table(name = "user_week_fact")
public class UserWeekFact {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	@Column(name = "plan_state")
	private String planState;

	@Column(name = "lock_compliance", nullable = false)
	private boolean lockCompliance;

	@Column(name = "reconcile_compliance", nullable = false)
	private boolean reconcileCompliance;

	@Column(name = "total_planned_points", nullable = false)
	private int totalPlannedPoints;

	@Column(name = "total_achieved_points", nullable = false)
	private int totalAchievedPoints;

	@Column(name = "commit_count", nullable = false)
	private int commitCount;

	@Column(name = "carry_forward_count", nullable = false)
	private int carryForwardCount;

	@Column(name = "scope_change_count", nullable = false)
	private int scopeChangeCount;

	@Column(name = "king_count", nullable = false)
	private int kingCount;

	@Column(name = "queen_count", nullable = false)
	private int queenCount;

	@Column(name = "refreshed_at", nullable = false)
	private Instant refreshedAt = Instant.now();

	// ---- Getters / Setters -----------------------------------------------

	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getUserId() {
		return userId;
	}
	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public LocalDate getWeekStart() {
		return weekStart;
	}
	public void setWeekStart(LocalDate weekStart) {
		this.weekStart = weekStart;
	}

	public String getPlanState() {
		return planState;
	}
	public void setPlanState(String planState) {
		this.planState = planState;
	}

	public boolean isLockCompliance() {
		return lockCompliance;
	}
	public void setLockCompliance(boolean lockCompliance) {
		this.lockCompliance = lockCompliance;
	}

	public boolean isReconcileCompliance() {
		return reconcileCompliance;
	}
	public void setReconcileCompliance(boolean reconcileCompliance) {
		this.reconcileCompliance = reconcileCompliance;
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

	public int getCommitCount() {
		return commitCount;
	}
	public void setCommitCount(int commitCount) {
		this.commitCount = commitCount;
	}

	public int getCarryForwardCount() {
		return carryForwardCount;
	}
	public void setCarryForwardCount(int carryForwardCount) {
		this.carryForwardCount = carryForwardCount;
	}

	public int getScopeChangeCount() {
		return scopeChangeCount;
	}
	public void setScopeChangeCount(int scopeChangeCount) {
		this.scopeChangeCount = scopeChangeCount;
	}

	public int getKingCount() {
		return kingCount;
	}
	public void setKingCount(int kingCount) {
		this.kingCount = kingCount;
	}

	public int getQueenCount() {
		return queenCount;
	}
	public void setQueenCount(int queenCount) {
		this.queenCount = queenCount;
	}

	public Instant getRefreshedAt() {
		return refreshedAt;
	}
	public void setRefreshedAt(Instant refreshedAt) {
		this.refreshedAt = refreshedAt;
	}
}
