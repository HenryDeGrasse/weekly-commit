package com.weeklycommit.ai.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single evaluation test case loaded from JSON fixture files.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(String id, String description, String edgeCaseType, JsonNode input, JsonNode expectedBehavior,
		boolean critical) {
}
