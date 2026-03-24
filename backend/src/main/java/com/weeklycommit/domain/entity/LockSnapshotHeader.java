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
@Table(name = "lock_snapshot_header")
public class LockSnapshotHeader {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "plan_id", nullable = false, unique = true)
	private UUID planId;

	@CreationTimestamp
	@Column(name = "locked_at", nullable = false, updatable = false)
	private Instant lockedAt;

	@Column(name = "locked_by_system", nullable = false)
	private boolean lockedBySystem = false;

	@NotBlank
	@Column(name = "snapshot_payload", nullable = false, columnDefinition = "text")
	private String snapshotPayload;

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

	public Instant getLockedAt() {
		return lockedAt;
	}

	public boolean isLockedBySystem() {
		return lockedBySystem;
	}

	public void setLockedBySystem(boolean lockedBySystem) {
		this.lockedBySystem = lockedBySystem;
	}

	public String getSnapshotPayload() {
		return snapshotPayload;
	}

	public void setSnapshotPayload(String snapshotPayload) {
		this.snapshotPayload = snapshotPayload;
	}
}
