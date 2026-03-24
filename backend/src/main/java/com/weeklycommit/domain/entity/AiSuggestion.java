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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_suggestion")
public class AiSuggestion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "plan_id")
	private UUID planId;

	@Column(name = "commit_id")
	private UUID commitId;

	@NotBlank
	@Column(name = "suggestion_type", nullable = false)
	private String suggestionType;

	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String prompt;

	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String rationale;

	@NotBlank
	@Column(name = "suggestion_payload", nullable = false, columnDefinition = "text")
	private String suggestionPayload;

	@NotBlank
	@Column(name = "model_version", nullable = false)
	private String modelVersion;

	@Column
	private Boolean accepted;

	@Column
	private Boolean dismissed;

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

	public String getSuggestionType() {
		return suggestionType;
	}

	public void setSuggestionType(String suggestionType) {
		this.suggestionType = suggestionType;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public String getRationale() {
		return rationale;
	}

	public void setRationale(String rationale) {
		this.rationale = rationale;
	}

	public String getSuggestionPayload() {
		return suggestionPayload;
	}

	public void setSuggestionPayload(String suggestionPayload) {
		this.suggestionPayload = suggestionPayload;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public Boolean getAccepted() {
		return accepted;
	}

	public void setAccepted(Boolean accepted) {
		this.accepted = accepted;
	}

	public Boolean getDismissed() {
		return dismissed;
	}

	public void setDismissed(Boolean dismissed) {
		this.dismissed = dismissed;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
