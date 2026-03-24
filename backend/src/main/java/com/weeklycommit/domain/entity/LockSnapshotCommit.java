package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "lock_snapshot_commit")
public class LockSnapshotCommit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "snapshot_id", nullable = false)
	private UUID snapshotId;

	@NotNull
	@Column(name = "commit_id", nullable = false)
	private UUID commitId;

	@NotBlank
	@Column(name = "snapshot_data", nullable = false, columnDefinition = "text")
	private String snapshotData;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getSnapshotId() {
		return snapshotId;
	}

	public void setSnapshotId(UUID snapshotId) {
		this.snapshotId = snapshotId;
	}

	public UUID getCommitId() {
		return commitId;
	}

	public void setCommitId(UUID commitId) {
		this.commitId = commitId;
	}

	public String getSnapshotData() {
		return snapshotData;
	}

	public void setSnapshotData(String snapshotData) {
		this.snapshotData = snapshotData;
	}
}
