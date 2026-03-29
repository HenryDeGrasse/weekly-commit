package com.weeklycommit.ai.dto;

import com.weeklycommit.ai.service.PlanRecommendationService.PlanRecommendation;
import java.util.List;
import java.util.UUID;

/**
 * API response DTO for a single plan recommendation.
 *
 * <p>
 * The {@link #suggestionId()} field is the primary key of the persisted
 * {@code AiSuggestion} row. The frontend uses this to post feedback via
 * {@code AiFeedbackButtons} and to track per-recommendation dismissal in local
 * storage across page reloads.
 *
 */
public record PlanRecommendationResponse(
		/**
		 * Stable suggestion row id — used for feedback buttons and dismissal tracking.
		 */
		UUID suggestionId,
		/**
		 * Risk signal type that triggered this recommendation (e.g.
		 * {@code "OVERCOMMIT"}).
		 */
		String riskType,
		/** Human-readable description of the detected risk. */
		String description,
		/** Suggested action to address the risk. */
		String suggestedAction,
		/**
		 * What-if simulation result. {@code null} only when simulation failed (graceful
		 * degradation).
		 */
		WhatIfResponse whatIfResult,
		/** Combined narrative from the risk signal and optional what-if result. */
		String narrative,
		/**
		 * Confidence tier name: {@code HIGH}, {@code MEDIUM}, {@code LOW}, or
		 * {@code INSUFFICIENT}.
		 */
		String confidence,
		/** {@code true} when this is a fully populated response. */
		boolean available) {

	/** Converts a domain {@link PlanRecommendation} to the response DTO. */
	public static PlanRecommendationResponse of(PlanRecommendation rec) {
		return new PlanRecommendationResponse(rec.suggestionId(), rec.riskType(), rec.description(),
				rec.suggestedAction(), rec.whatIfResult(), rec.narrative(),
				rec.confidence() != null ? rec.confidence().name() : null, true);
	}

	/** Batch conversion. */
	public static List<PlanRecommendationResponse> ofList(List<PlanRecommendation> recs) {
		return recs.stream().map(PlanRecommendationResponse::of).toList();
	}
}
