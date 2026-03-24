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
@Table(name = "rcdo_change_log")
public class RcdoChangeLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "rcdo_node_id", nullable = false)
	private UUID rcdoNodeId;

	@NotNull
	@Column(name = "changed_by_user_id", nullable = false)
	private UUID changedByUserId;

	@NotBlank
	@Column(name = "change_summary", nullable = false)
	private String changeSummary;

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

	public UUID getRcdoNodeId() {
		return rcdoNodeId;
	}

	public void setRcdoNodeId(UUID rcdoNodeId) {
		this.rcdoNodeId = rcdoNodeId;
	}

	public UUID getChangedByUserId() {
		return changedByUserId;
	}

	public void setChangedByUserId(UUID changedByUserId) {
		this.changedByUserId = changedByUserId;
	}

	public String getChangeSummary() {
		return changeSummary;
	}

	public void setChangeSummary(String changeSummary) {
		this.changeSummary = changeSummary;
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
