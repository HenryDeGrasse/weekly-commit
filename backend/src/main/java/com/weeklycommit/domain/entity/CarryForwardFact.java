package com.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Derived read-model: one row per active carry-forward commit, tracking
 * provenance and streak length.
 */
@Entity
@Table(name = "carry_forward_fact")
public class CarryForwardFact {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	/** The current (target) commit in the chain. */
	@Column(name = "commit_id", nullable = false)
	private UUID commitId;

	/** The week in which the original (root) commit was created. */
	@Column(name = "source_week", nullable = false)
	private LocalDate sourceWeek;

	/** The week in which this commit currently lives. */
	@Column(name = "current_week", nullable = false)
	private LocalDate currentWeek;

	/** Number of consecutive weeks this work has been carried forward. */
	@Column(name = "streak_length", nullable = false)
	private int streakLength;

	@Column(name = "rcdo_node_id")
	private UUID rcdoNodeId;

	@Column(name = "chess_piece")
	private String chessPiece;

	@Column(name = "refreshed_at", nullable = false)
	private Instant refreshedAt = Instant.now();

	// ---- Getters / Setters -----------------------------------------------

	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getCommitId() {
		return commitId;
	}
	public void setCommitId(UUID commitId) {
		this.commitId = commitId;
	}

	public LocalDate getSourceWeek() {
		return sourceWeek;
	}
	public void setSourceWeek(LocalDate sourceWeek) {
		this.sourceWeek = sourceWeek;
	}

	public LocalDate getCurrentWeek() {
		return currentWeek;
	}
	public void setCurrentWeek(LocalDate currentWeek) {
		this.currentWeek = currentWeek;
	}

	public int getStreakLength() {
		return streakLength;
	}
	public void setStreakLength(int streakLength) {
		this.streakLength = streakLength;
	}

	public UUID getRcdoNodeId() {
		return rcdoNodeId;
	}
	public void setRcdoNodeId(UUID rcdoNodeId) {
		this.rcdoNodeId = rcdoNodeId;
	}

	public String getChessPiece() {
		return chessPiece;
	}
	public void setChessPiece(String chessPiece) {
		this.chessPiece = chessPiece;
	}

	public Instant getRefreshedAt() {
		return refreshedAt;
	}
	public void setRefreshedAt(Instant refreshedAt) {
		this.refreshedAt = refreshedAt;
	}
}
