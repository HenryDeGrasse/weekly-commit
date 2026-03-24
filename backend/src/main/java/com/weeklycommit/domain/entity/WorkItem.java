package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "work_item")
public class WorkItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@NotBlank
	@Column(nullable = false)
	private String key;

	@NotBlank
	@Column(nullable = false)
	private String title;

	@Column
	private String description;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketStatus status = TicketStatus.TODO;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketPriority priority = TicketPriority.MEDIUM;

	@Column(name = "assignee_user_id")
	private UUID assigneeUserId;

	@NotNull
	@Column(name = "reporter_user_id", nullable = false)
	private UUID reporterUserId;

	@Column(name = "estimate_points")
	private Integer estimatePoints;

	@Column(name = "rcdo_node_id")
	private UUID rcdoNodeId;

	@Column(name = "target_week_start_date")
	private LocalDate targetWeekStartDate;

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

	public UUID getTeamId() {
		return teamId;
	}

	public void setTeamId(UUID teamId) {
		this.teamId = teamId;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public void setStatus(TicketStatus status) {
		this.status = status;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public void setPriority(TicketPriority priority) {
		this.priority = priority;
	}

	public UUID getAssigneeUserId() {
		return assigneeUserId;
	}

	public void setAssigneeUserId(UUID assigneeUserId) {
		this.assigneeUserId = assigneeUserId;
	}

	public UUID getReporterUserId() {
		return reporterUserId;
	}

	public void setReporterUserId(UUID reporterUserId) {
		this.reporterUserId = reporterUserId;
	}

	public Integer getEstimatePoints() {
		return estimatePoints;
	}

	public void setEstimatePoints(Integer estimatePoints) {
		this.estimatePoints = estimatePoints;
	}

	public UUID getRcdoNodeId() {
		return rcdoNodeId;
	}

	public void setRcdoNodeId(UUID rcdoNodeId) {
		this.rcdoNodeId = rcdoNodeId;
	}

	public LocalDate getTargetWeekStartDate() {
		return targetWeekStartDate;
	}

	public void setTargetWeekStartDate(LocalDate targetWeekStartDate) {
		this.targetWeekStartDate = targetWeekStartDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
