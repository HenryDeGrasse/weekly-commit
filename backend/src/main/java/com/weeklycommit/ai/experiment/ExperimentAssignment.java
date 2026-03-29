package com.weeklycommit.ai.experiment;

/**
 * Immutable result of an experiment variant assignment.
 *
 * @param experimentName
 *            the experiment key (e.g. {@code "llm-model"})
 * @param variant
 *            {@code "control"} or {@code "treatment"}
 * @param value
 *            the resolved config value for the assigned variant
 */
public record ExperimentAssignment(String experimentName, String variant, String value) {

	/** Returns {@code true} if the treatment arm was assigned. */
	public boolean isTreatment() {
		return ExperimentService.VARIANT_TREATMENT.equals(variant);
	}

	/** Returns {@code true} if the control arm was assigned. */
	public boolean isControl() {
		return ExperimentService.VARIANT_CONTROL.equals(variant);
	}
}
