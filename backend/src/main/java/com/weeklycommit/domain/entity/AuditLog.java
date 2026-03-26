package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;

/** Append-only audit record. Never updated or deleted after creation. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	/** UserRole name of the acting user (IC / MANAGER / ADMIN / SYSTEM). */
	@Column(name = "actor_role", updatable = false)
	private String actorRole;

	@NotBlank
	@Column(nullable = false, updatable = false)
	private String action;

	@NotBlank
	@Column(name = "entity_type", nullable = false, updatable = false)
	private String entityType;

	@Column(name = "entity_id", updatable = false)
	private UUID entityId;

	/** JSON snapshot of the entity state before the action. */
	@ColumnTransformer(write = "?::jsonb")
	@Column(name = "old_value", updatable = false, columnDefinition = "jsonb")
	private String oldValue;

	/** JSON snapshot of the entity state after the action. */
	@ColumnTransformer(write = "?::jsonb")
	@Column(name = "new_value", updatable = false, columnDefinition = "jsonb")
	private String newValue;

	@Column(name = "ip_address", updatable = false)
	private String ipAddress;

	@Column(name = "user_agent", updatable = false)
	private String userAgent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	public UUID getId() {
		return id;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public void setActorUserId(UUID actorUserId) {
		this.actorUserId = actorUserId;
	}

	public String getActorRole() {
		return actorRole;
	}

	public void setActorRole(String actorRole) {
		this.actorRole = actorRole;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public void setEntityId(UUID entityId) {
		this.entityId = entityId;
	}

	public String getOldValue() {
		return oldValue;
	}

	public void setOldValue(String oldValue) {
		this.oldValue = oldValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public void setNewValue(String newValue) {
		this.newValue = newValue;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
