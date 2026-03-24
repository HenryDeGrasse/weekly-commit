package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Persisted exception record that requires manager review and resolution. */
@Entity
@Table(name = "manager_review_exception")
public class ManagerReviewException {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@Column(name = "plan_id")
	private UUID planId;

	@NotNull
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "exception_type", nullable = false)
	private ExceptionType exceptionType;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExceptionSeverity severity;

	@NotBlank
	@Column(nullable = false)
	private String description;

	@NotNull
	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(nullable = false)
	private boolean resolved = false;

	@Column
	private String resolution;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "resolved_by_id")
	private UUID resolvedById;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

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

	public UUID getPlanId() {
		return planId;
	}

	public void setPlanId(UUID planId) {
		this.planId = planId;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public ExceptionType getExceptionType() {
		return exceptionType;
	}

	public void setExceptionType(ExceptionType exceptionType) {
		this.exceptionType = exceptionType;
	}

	public ExceptionSeverity getSeverity() {
		return severity;
	}

	public void setSeverity(ExceptionSeverity severity) {
		this.severity = severity;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getWeekStartDate() {
		return weekStartDate;
	}

	public void setWeekStartDate(LocalDate weekStartDate) {
		this.weekStartDate = weekStartDate;
	}

	public boolean isResolved() {
		return resolved;
	}

	public void setResolved(boolean resolved) {
		this.resolved = resolved;
	}

	public String getResolution() {
		return resolution;
	}

	public void setResolution(String resolution) {
		this.resolution = resolution;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public UUID getResolvedById() {
		return resolvedById;
	}

	public void setResolvedById(UUID resolvedById) {
		this.resolvedById = resolvedById;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
