package com.weeklycommit.ai.dto;

import com.weeklycommit.ai.service.CalibrationService.CalibrationConfidenceTier;
import com.weeklycommit.ai.service.CalibrationService.CalibrationProfile;
import com.weeklycommit.domain.enums.ChessPiece;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API response DTO for the calibration profile endpoint.
 *
 * <p>
 * Chess-piece keys are serialised as their enum name strings so the frontend
 * can display per-piece stats without depending on the Java enum type.
 */
public record CalibrationProfileResponse(
		/** {@code false} when there is insufficient data to produce a profile. */
		boolean available,
		/** Overall achievement rate [0.0–1.0]; {@code 0.0} when unavailable. */
		double overallAchievementRate,
		/** Per-chess-piece achievement rates, keyed by piece name. */
		Map<String, Double> chessPieceAchievementRates,
		/** Fraction of commits carried forward [0.0–1.0]. */
		double carryForwardProbability,
		/** Number of weeks of data used. */
		int weeksOfData,
		/** Average estimate points per chess piece, keyed by piece name. */
		Map<String, Double> avgEstimateByPiece,
		/**
		 * Data-sufficiency tier: HIGH, MEDIUM, LOW, or INSUFFICIENT.
		 */
		String confidenceTier) {

	/** Converts a domain {@link CalibrationProfile} to the response DTO. */
	public static CalibrationProfileResponse of(CalibrationProfile profile) {
		if (profile == null || profile.isInsufficient()) {
			return unavailable();
		}
		return new CalibrationProfileResponse(true, profile.overallAchievementRate(),
				toStringKeys(profile.chessPieceAchievementRates()), profile.carryForwardProbability(),
				profile.weeksOfData(), toStringKeys(profile.avgEstimateByPiece()), profile.confidenceTier().name());
	}

	/** Sentinel response when data is insufficient or an error occurs. */
	public static CalibrationProfileResponse unavailable() {
		return new CalibrationProfileResponse(false, 0.0, Map.of(), 0.0, 0, Map.of(),
				CalibrationConfidenceTier.INSUFFICIENT.name());
	}

	private static Map<String, Double> toStringKeys(Map<ChessPiece, Double> pieceMap) {
		if (pieceMap == null || pieceMap.isEmpty()) {
			return Map.of();
		}
		return pieceMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
	}
}
