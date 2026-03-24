package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.ScopeChangeCategory;
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
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "scope_change_event")
public class ScopeChangeEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "plan_id", nullable = false)
	private UUID planId;

	@Column(name = "commit_id")
	private UUID commitId;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ScopeChangeCategory category;

	@NotNull
	@Column(name = "changed_by_user_id", nullable = false)
	private UUID changedByUserId;

	@NotBlank
	@Column(nullable = false)
	private String reason;

	@Column(name = "previous_value")
	private String previousValue;

	@Column(name = "new_value")
	private String newValue;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

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

	public UUID getCommitId() {
		return commitId;
	}

	public void setCommitId(UUID commitId) {
		this.commitId = commitId;
	}

	public ScopeChangeCategory getCategory() {
		return category;
	}

	public void setCategory(ScopeChangeCategory category) {
		this.category = category;
	}

	public UUID getChangedByUserId() {
		return changedByUserId;
	}

	public void setChangedByUserId(UUID changedByUserId) {
		this.changedByUserId = changedByUserId;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getPreviousValue() {
		return previousValue;
	}

	public void setPreviousValue(String previousValue) {
		this.previousValue = previousValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public void setNewValue(String newValue) {
		this.newValue = newValue;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
