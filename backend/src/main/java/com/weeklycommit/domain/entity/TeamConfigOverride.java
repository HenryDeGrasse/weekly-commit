package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Team-level cadence and capacity overrides. Each nullable field represents an
 * override; a {@code null} value means "use the org-level default from
 * {@link OrgConfig}".
 */
@Entity
@Table(name = "team_config_override")
public class TeamConfigOverride {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	/** Nullable: if null, falls back to {@link OrgConfig#getWeekStartDay()}. */
	@Column(name = "week_start_day")
	private String weekStartDay;

	/**
	 * Nullable: if null, falls back to {@link OrgConfig#getDraftOpenOffsetHours()}.
	 */
	@Column(name = "draft_open_offset_hours")
	private Integer draftOpenOffsetHours;

	/**
	 * Nullable: if null, falls back to {@link OrgConfig#getLockDueOffsetHours()}.
	 */
	@Column(name = "lock_due_offset_hours")
	private Integer lockDueOffsetHours;

	/**
	 * Nullable: if null, falls back to
	 * {@link OrgConfig#getReconcileOpenOffsetHours()}.
	 */
	@Column(name = "reconcile_open_offset_hours")
	private Integer reconcileOpenOffsetHours;

	/**
	 * Nullable: if null, falls back to
	 * {@link OrgConfig#getReconcileDueOffsetHours()}.
	 */
	@Column(name = "reconcile_due_offset_hours")
	private Integer reconcileDueOffsetHours;

	/**
	 * Nullable: if null, falls back to {@link OrgConfig#getDefaultWeeklyBudget()}.
	 */
	@Column(name = "default_weekly_budget")
	private Integer defaultWeeklyBudget;

	/** Nullable: if null, falls back to {@link OrgConfig#getTimezone()}. */
	@Column
	private String timezone;

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

	public UUID getTeamId() {
		return teamId;
	}

	public void setTeamId(UUID teamId) {
		this.teamId = teamId;
	}

	public String getWeekStartDay() {
		return weekStartDay;
	}

	public void setWeekStartDay(String weekStartDay) {
		this.weekStartDay = weekStartDay;
	}

	public Integer getDraftOpenOffsetHours() {
		return draftOpenOffsetHours;
	}

	public void setDraftOpenOffsetHours(Integer draftOpenOffsetHours) {
		this.draftOpenOffsetHours = draftOpenOffsetHours;
	}

	public Integer getLockDueOffsetHours() {
		return lockDueOffsetHours;
	}

	public void setLockDueOffsetHours(Integer lockDueOffsetHours) {
		this.lockDueOffsetHours = lockDueOffsetHours;
	}

	public Integer getReconcileOpenOffsetHours() {
		return reconcileOpenOffsetHours;
	}

	public void setReconcileOpenOffsetHours(Integer reconcileOpenOffsetHours) {
		this.reconcileOpenOffsetHours = reconcileOpenOffsetHours;
	}

	public Integer getReconcileDueOffsetHours() {
		return reconcileDueOffsetHours;
	}

	public void setReconcileDueOffsetHours(Integer reconcileDueOffsetHours) {
		this.reconcileDueOffsetHours = reconcileDueOffsetHours;
	}

	public Integer getDefaultWeeklyBudget() {
		return defaultWeeklyBudget;
	}

	public void setDefaultWeeklyBudget(Integer defaultWeeklyBudget) {
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
