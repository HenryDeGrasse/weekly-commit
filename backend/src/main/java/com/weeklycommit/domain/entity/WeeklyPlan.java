package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.PlanState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "weekly_plan")
public class WeeklyPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@NotNull
	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@NotNull
	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlanState state = PlanState.DRAFT;

	@NotNull
	@Column(name = "lock_deadline", nullable = false)
	private Instant lockDeadline;

	@NotNull
	@Column(name = "reconcile_deadline", nullable = false)
	private Instant reconcileDeadline;

	@Positive
	@Column(name = "capacity_budget_points", nullable = false)
	private int capacityBudgetPoints = 10;

	@Column(name = "is_compliant", nullable = false)
	private boolean isCompliant = true;

	@Column(name = "system_locked_with_errors", nullable = false)
	private boolean systemLockedWithErrors = false;

	@Column(name = "lock_snapshot_id")
	private UUID lockSnapshotId;

	@Column(name = "reconcile_snapshot_id")
	private UUID reconcileSnapshotId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public void setOwnerUserId(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public UUID getTeamId() {
		return teamId;
	}

	public void setTeamId(UUID teamId) {
		this.teamId = teamId;
	}

	public LocalDate getWeekStartDate() {
		return weekStartDate;
	}

	public void setWeekStartDate(LocalDate weekStartDate) {
		this.weekStartDate = weekStartDate;
	}

	public PlanState getState() {
		return state;
	}

	public void setState(PlanState state) {
		this.state = state;
	}

	public Instant getLockDeadline() {
		return lockDeadline;
	}

	public void setLockDeadline(Instant lockDeadline) {
		this.lockDeadline = lockDeadline;
	}

	public Instant getReconcileDeadline() {
		return reconcileDeadline;
	}

	public void setReconcileDeadline(Instant reconcileDeadline) {
		this.reconcileDeadline = reconcileDeadline;
	}

	public int getCapacityBudgetPoints() {
		return capacityBudgetPoints;
	}

	public void setCapacityBudgetPoints(int capacityBudgetPoints) {
		this.capacityBudgetPoints = capacityBudgetPoints;
	}

	public boolean isCompliant() {
		return isCompliant;
	}

	public void setCompliant(boolean compliant) {
		isCompliant = compliant;
	}

	public boolean isSystemLockedWithErrors() {
		return systemLockedWithErrors;
	}

	public void setSystemLockedWithErrors(boolean systemLockedWithErrors) {
		this.systemLockedWithErrors = systemLockedWithErrors;
	}

	public UUID getLockSnapshotId() {
		return lockSnapshotId;
	}

	public void setLockSnapshotId(UUID lockSnapshotId) {
		this.lockSnapshotId = lockSnapshotId;
	}

	public UUID getReconcileSnapshotId() {
		return reconcileSnapshotId;
	}

	public void setReconcileSnapshotId(UUID reconcileSnapshotId) {
		this.reconcileSnapshotId = reconcileSnapshotId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
