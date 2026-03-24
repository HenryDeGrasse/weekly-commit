package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_feedback")
public class AiFeedback {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "suggestion_id", nullable = false)
	private UUID suggestionId;

	@NotNull
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private boolean accepted;

	@Column(name = "feedback_notes")
	private String feedbackNotes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getSuggestionId() {
		return suggestionId;
	}

	public void setSuggestionId(UUID suggestionId) {
		this.suggestionId = suggestionId;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public boolean isAccepted() {
		return accepted;
	}

	public void setAccepted(boolean accepted) {
		this.accepted = accepted;
	}

	public String getFeedbackNotes() {
		return feedbackNotes;
	}

	public void setFeedbackNotes(String feedbackNotes) {
		this.feedbackNotes = feedbackNotes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
