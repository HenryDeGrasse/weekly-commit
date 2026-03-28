package com.weeklycommit.ai.dto;

import com.weeklycommit.ai.evidence.StructuredEvidence;

/**
 * API response wrapping a {@link StructuredEvidence} bundle. Includes an
 * availability flag so the frontend can degrade gracefully when evidence
 * assembly fails.
 */
public record StructuredEvidenceResponse(boolean available, StructuredEvidence evidence) {

	public static StructuredEvidenceResponse unavailable() {
		return new StructuredEvidenceResponse(false, null);
	}

	public static StructuredEvidenceResponse of(StructuredEvidence evidence) {
		return new StructuredEvidenceResponse(true, evidence);
	}
}
