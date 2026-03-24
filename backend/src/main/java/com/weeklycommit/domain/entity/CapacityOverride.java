package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "capacity_override")
public class CapacityOverride {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@NotNull
	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Positive
	@Column(name = "budget_points", nullable = false)
	private int budgetPoints;

	@Column
	private String reason;

	@NotNull
	@Column(name = "set_by_manager_id", nullable = false)
	private UUID setByManagerId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

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

	public LocalDate getWeekStartDate() {
		return weekStartDate;
	}

	public void setWeekStartDate(LocalDate weekStartDate) {
		this.weekStartDate = weekStartDate;
	}

	public int getBudgetPoints() {
		return budgetPoints;
	}

	public void setBudgetPoints(int budgetPoints) {
		this.budgetPoints = budgetPoints;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public UUID getSetByManagerId() {
		return setByManagerId;
	}

	public void setSetByManagerId(UUID setByManagerId) {
		this.setByManagerId = setByManagerId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
