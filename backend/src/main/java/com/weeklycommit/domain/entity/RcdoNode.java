package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "rcdo_node")
public class RcdoNode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "node_type", nullable = false)
	private RcdoNodeType nodeType;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RcdoNodeStatus status = RcdoNodeStatus.DRAFT;

	@Column(name = "parent_id")
	private UUID parentId;

	@NotBlank
	@Column(nullable = false)
	private String title;

	@Column
	private String description;

	@Column(name = "owner_team_id")
	private UUID ownerTeamId;

	@Column(name = "owner_user_id")
	private UUID ownerUserId;

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

	public RcdoNodeType getNodeType() {
		return nodeType;
	}

	public void setNodeType(RcdoNodeType nodeType) {
		this.nodeType = nodeType;
	}

	public RcdoNodeStatus getStatus() {
		return status;
	}

	public void setStatus(RcdoNodeStatus status) {
		this.status = status;
	}

	public UUID getParentId() {
		return parentId;
	}

	public void setParentId(UUID parentId) {
		this.parentId = parentId;
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

	public UUID getOwnerTeamId() {
		return ownerTeamId;
	}

	public void setOwnerTeamId(UUID ownerTeamId) {
		this.ownerTeamId = ownerTeamId;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public void setOwnerUserId(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
