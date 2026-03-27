package com.weeklycommit.ai.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of evaluating a single test case. Collects pass/fail verdicts and
 * numeric scores per dimension.
 */
public class EvalResult {

	private final String caseId;
	private final String description;
	private final String promptVersion;
	private final boolean schemaValid;
	private final String rawOutput;
	private final Map<String, Double> scores = new LinkedHashMap<>();
	private final Map<String, Boolean> checks = new LinkedHashMap<>();
	private final Map<String, String> notes = new LinkedHashMap<>();

	public EvalResult(String caseId, String description, String promptVersion, boolean schemaValid, String rawOutput) {
		this.caseId = caseId;
		this.description = description;
		this.promptVersion = promptVersion;
		this.schemaValid = schemaValid;
		this.rawOutput = rawOutput;
	}

	public void addScore(String dimension, double score) {
		scores.put(dimension, score);
	}

	public void addCheck(String name, boolean passed) {
		checks.put(name, passed);
	}

	public void addNote(String key, String value) {
		notes.put(key, value);
	}

	public boolean passed() {
		if (!schemaValid)
			return false;
		return checks.values().stream().allMatch(Boolean::booleanValue);
	}

	public String getCaseId() {
		return caseId;
	}

	public String getDescription() {
		return description;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public boolean isSchemaValid() {
		return schemaValid;
	}

	public String getRawOutput() {
		return rawOutput;
	}

	public Map<String, Double> getScores() {
		return scores;
	}

	public Map<String, Boolean> getChecks() {
		return checks;
	}

	public Map<String, String> getNotes() {
		return notes;
	}

	@Override
	public String toString() {
		return String.format("[%s] %s — schema=%s passed=%s scores=%s checks=%s", caseId, description, schemaValid,
				passed(), scores, checks);
	}
}
