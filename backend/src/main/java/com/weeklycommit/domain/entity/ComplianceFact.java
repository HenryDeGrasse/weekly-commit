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
 * Derived read-model: per-user per-week lock/reconcile compliance detail.
 */
@Entity
@Table(name = "compliance_fact")
public class ComplianceFact {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	/** Plan was manually locked before the deadline. */
	@Column(name = "lock_on_time", nullable = false)
	private boolean lockOnTime;

	/** Plan was manually locked after the deadline (still by the user). */
	@Column(name = "lock_late", nullable = false)
	private boolean lockLate;

	/** Plan was auto-locked by the system (user missed the deadline). */
	@Column(name = "auto_locked", nullable = false)
	private boolean autoLocked;

	/** Plan was reconciled (simplified: true when state = RECONCILED). */
	@Column(name = "reconcile_on_time", nullable = false)
	private boolean reconcileOnTime;

	/**
	 * Reconciliation was late (reserved for future use when reconciled_at is
	 * tracked).
	 */
	@Column(name = "reconcile_late", nullable = false)
	private boolean reconcileLate;

	/** Reconcile deadline passed but plan is not yet RECONCILED. */
	@Column(name = "reconcile_missed", nullable = false)
	private boolean reconcileMissed;

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

	public boolean isLockOnTime() {
		return lockOnTime;
	}
	public void setLockOnTime(boolean lockOnTime) {
		this.lockOnTime = lockOnTime;
	}

	public boolean isLockLate() {
		return lockLate;
	}
	public void setLockLate(boolean lockLate) {
		this.lockLate = lockLate;
	}

	public boolean isAutoLocked() {
		return autoLocked;
	}
	public void setAutoLocked(boolean autoLocked) {
		this.autoLocked = autoLocked;
	}

	public boolean isReconcileOnTime() {
		return reconcileOnTime;
	}
	public void setReconcileOnTime(boolean reconcileOnTime) {
		this.reconcileOnTime = reconcileOnTime;
	}

	public boolean isReconcileLate() {
		return reconcileLate;
	}
	public void setReconcileLate(boolean reconcileLate) {
		this.reconcileLate = reconcileLate;
	}

	public boolean isReconcileMissed() {
		return reconcileMissed;
	}
	public void setReconcileMissed(boolean reconcileMissed) {
		this.reconcileMissed = reconcileMissed;
	}

	public Instant getRefreshedAt() {
		return refreshedAt;
	}
	public void setRefreshedAt(Instant refreshedAt) {
		this.refreshedAt = refreshedAt;
	}
}
