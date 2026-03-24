package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "work_item_status_history")
public class WorkItemStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "work_item_id", nullable = false)
	private UUID workItemId;

	@Column(name = "from_status")
	private String fromStatus;

	@NotBlank
	@Column(name = "to_status", nullable = false)
	private String toStatus;

	@NotNull
	@Column(name = "changed_by_user_id", nullable = false)
	private UUID changedByUserId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getWorkItemId() {
		return workItemId;
	}

	public void setWorkItemId(UUID workItemId) {
		this.workItemId = workItemId;
	}

	public String getFromStatus() {
		return fromStatus;
	}

	public void setFromStatus(String fromStatus) {
		this.fromStatus = fromStatus;
	}

	public String getToStatus() {
		return toStatus;
	}

	public void setToStatus(String toStatus) {
		this.toStatus = toStatus;
	}

	public UUID getChangedByUserId() {
		return changedByUserId;
	}

	public void setChangedByUserId(UUID changedByUserId) {
		this.changedByUserId = changedByUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
