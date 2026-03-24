package com.weeklycommit.domain.entity;

import com.weeklycommit.domain.enums.CarryForwardReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "carry_forward_link")
public class CarryForwardLink {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "source_commit_id", nullable = false)
	private UUID sourceCommitId;

	@NotNull
	@Column(name = "target_commit_id", nullable = false)
	private UUID targetCommitId;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CarryForwardReason reason;

	@Column(name = "reason_notes")
	private String reasonNotes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getSourceCommitId() {
		return sourceCommitId;
	}

	public void setSourceCommitId(UUID sourceCommitId) {
		this.sourceCommitId = sourceCommitId;
	}

	public UUID getTargetCommitId() {
		return targetCommitId;
	}

	public void setTargetCommitId(UUID targetCommitId) {
		this.targetCommitId = targetCommitId;
	}

	public CarryForwardReason getReason() {
		return reason;
	}

	public void setReason(CarryForwardReason reason) {
		this.reason = reason;
	}

	public String getReasonNotes() {
		return reasonNotes;
	}

	public void setReasonNotes(String reasonNotes) {
		this.reasonNotes = reasonNotes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
