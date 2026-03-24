package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Organisation-level cadence configuration. A single row per organisation
 * stores the default planning-week cadence and budget. Each field defaults to
 * the PRD §12 recommended value:
 * <ul>
 * <li>lock due offset: 12 h from week-start (Mon 12:00)</li>
 * <li>reconcile open offset: 113 h (Fri 17:00)</li>
 * <li>reconcile due offset: 178 h (next Mon 10:00)</li>
 * <li>default weekly budget: 10 points</li>
 * </ul>
 */
@Entity
@Table(name = "org_config")
public class OrgConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "org_id", nullable = false)
	private UUID orgId;

	@NotBlank
	@Column(name = "week_start_day", nullable = false)
	private String weekStartDay = "MONDAY";

	/**
	 * Hours from week-start until the draft/planning window opens. Default -60 =
	 * previous Friday 12:00 for a Monday-start week.
	 */
	@Column(name = "draft_open_offset_hours", nullable = false)
	private int draftOpenOffsetHours = -60;

	/**
	 * Hours from week-start (Monday 00:00) until the lock deadline. Default 12 =
	 * Mon 12:00.
	 */
	@Column(name = "lock_due_offset_hours", nullable = false)
	private int lockDueOffsetHours = 12;

	/**
	 * Hours from week-start until reconcile window opens. Default 113 = 4 days 17 h
	 * = Fri 17:00.
	 */
	@Column(name = "reconcile_open_offset_hours", nullable = false)
	private int reconcileOpenOffsetHours = 113;

	/**
	 * Hours from week-start until reconcile is due. Default 178 = 7 days 10 h =
	 * next Mon 10:00.
	 */
	@Column(name = "reconcile_due_offset_hours", nullable = false)
	private int reconcileDueOffsetHours = 178;

	@Positive
	@Column(name = "default_weekly_budget", nullable = false)
	private int defaultWeeklyBudget = 10;

	@NotBlank
	@Column(nullable = false)
	private String timezone = "UTC";

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// -------------------------------------------------------------------------
	// Getters and setters
	// -------------------------------------------------------------------------

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getOrgId() {
		return orgId;
	}

	public void setOrgId(UUID orgId) {
		this.orgId = orgId;
	}

	public String getWeekStartDay() {
		return weekStartDay;
	}

	public void setWeekStartDay(String weekStartDay) {
		this.weekStartDay = weekStartDay;
	}

	public int getDraftOpenOffsetHours() {
		return draftOpenOffsetHours;
	}

	public void setDraftOpenOffsetHours(int draftOpenOffsetHours) {
		this.draftOpenOffsetHours = draftOpenOffsetHours;
	}

	public int getLockDueOffsetHours() {
		return lockDueOffsetHours;
	}

	public void setLockDueOffsetHours(int lockDueOffsetHours) {
		this.lockDueOffsetHours = lockDueOffsetHours;
	}

	public int getReconcileOpenOffsetHours() {
		return reconcileOpenOffsetHours;
	}

	public void setReconcileOpenOffsetHours(int reconcileOpenOffsetHours) {
		this.reconcileOpenOffsetHours = reconcileOpenOffsetHours;
	}

	public int getReconcileDueOffsetHours() {
		return reconcileDueOffsetHours;
	}

	public void setReconcileDueOffsetHours(int reconcileDueOffsetHours) {
		this.reconcileDueOffsetHours = reconcileDueOffsetHours;
	}

	public int getDefaultWeeklyBudget() {
		return defaultWeeklyBudget;
	}

	public void setDefaultWeeklyBudget(int defaultWeeklyBudget) {
		this.defaultWeeklyBudget = defaultWeeklyBudget;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
