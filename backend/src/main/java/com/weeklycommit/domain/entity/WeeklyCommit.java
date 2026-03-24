package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "weekly_commit")
public class WeeklyCommit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "plan_id", nullable = false)
	private UUID planId;

	@NotNull
	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@NotBlank
	@Column(nullable = false)
	private String title;

	@Column
	private String description;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "chess_piece", nullable = false)
	private ChessPiece chessPiece;

	@Min(1)
	@Column(name = "priority_order", nullable = false)
	private int priorityOrder;

	@Column(name = "rcdo_node_id")
	private UUID rcdoNodeId;

	@Column(name = "work_item_id")
	private UUID workItemId;

	@Column(name = "estimate_points")
	private Integer estimatePoints;

	@Column(name = "success_criteria")
	private String successCriteria;

	@Enumerated(EnumType.STRING)
	@Column
	private CommitOutcome outcome;

	@Column(name = "outcome_notes")
	private String outcomeNotes;

	@Column(name = "carry_forward_source_id")
	private UUID carryForwardSourceId;

	@Min(0)
	@Column(name = "carry_forward_streak", nullable = false)
	private int carryForwardStreak = 0;

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

	public UUID getPlanId() {
		return planId;
	}

	public void setPlanId(UUID planId) {
		this.planId = planId;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public void setOwnerUserId(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
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

	public ChessPiece getChessPiece() {
		return chessPiece;
	}

	public void setChessPiece(ChessPiece chessPiece) {
		this.chessPiece = chessPiece;
	}

	public int getPriorityOrder() {
		return priorityOrder;
	}

	public void setPriorityOrder(int priorityOrder) {
		this.priorityOrder = priorityOrder;
	}

	public UUID getRcdoNodeId() {
		return rcdoNodeId;
	}

	public void setRcdoNodeId(UUID rcdoNodeId) {
		this.rcdoNodeId = rcdoNodeId;
	}

	public UUID getWorkItemId() {
		return workItemId;
	}

	public void setWorkItemId(UUID workItemId) {
		this.workItemId = workItemId;
	}

	public Integer getEstimatePoints() {
		return estimatePoints;
	}

	public void setEstimatePoints(Integer estimatePoints) {
		this.estimatePoints = estimatePoints;
	}

	public String getSuccessCriteria() {
		return successCriteria;
	}

	public void setSuccessCriteria(String successCriteria) {
		this.successCriteria = successCriteria;
	}

	public CommitOutcome getOutcome() {
		return outcome;
	}

	public void setOutcome(CommitOutcome outcome) {
		this.outcome = outcome;
	}

	public String getOutcomeNotes() {
		return outcomeNotes;
	}

	public void setOutcomeNotes(String outcomeNotes) {
		this.outcomeNotes = outcomeNotes;
	}

	public UUID getCarryForwardSourceId() {
		return carryForwardSourceId;
	}

	public void setCarryForwardSourceId(UUID carryForwardSourceId) {
		this.carryForwardSourceId = carryForwardSourceId;
	}

	public int getCarryForwardStreak() {
		return carryForwardStreak;
	}

	public void setCarryForwardStreak(int carryForwardStreak) {
		this.carryForwardStreak = carryForwardStreak;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
