package com.weeklycommit.ai.dto;

import java.util.List;

/**
 * Response from the insight-list endpoints.
 *
 * @param aiAvailable
 *            {@code false} when no AI pipeline is configured or an error
 *            occurred; an empty {@code insights} list is still a valid response
 * @param insights
 *            ordered list of insight cards for the requested scope
 */
public record InsightListResponse(boolean aiAvailable, List<InsightCardDto> insights) {

	/** Convenience factory for an error / unavailable response. */
	public static InsightListResponse unavailable() {
		return new InsightListResponse(false, List.of());
	}
}
