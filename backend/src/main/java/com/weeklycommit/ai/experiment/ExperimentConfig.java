package com.weeklycommit.ai.experiment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AI pipeline A/B experiments.
 *
 * <p>
 * Loaded from the {@code ai.experiments} prefix in {@code application.yml}.
 * Each entry in the {@code experiments} map is keyed by experiment name (e.g.
 * {@code llm-model}) and carries the full variant definition.
 *
 * <p>
 * All experiments default to {@code enabled: false} and
 * {@code controlWeight: 1.0}, so existing behaviour is unchanged unless an
 * experiment is explicitly enabled with a lower control weight.
 */
@Configuration
@ConfigurationProperties(prefix = "ai.experiments")
public class ExperimentConfig {

	private Map<String, ExperimentDefinition> experiments = new LinkedHashMap<>();

	public Map<String, ExperimentDefinition> getExperiments() {
		return experiments;
	}

	public void setExperiments(Map<String, ExperimentDefinition> experiments) {
		this.experiments = experiments;
	}

	/**
	 * Defines a single experiment's variant configuration.
	 *
	 * <p>
	 * Mutable plain bean so that Spring's {@code @ConfigurationProperties} binding
	 * can populate it via setters.
	 */
	public static class ExperimentDefinition {

		/** Whether the experiment is active. Defaults to {@code false}. */
		private boolean enabled = false;

		/**
		 * Fraction of traffic that receives the control variant (0.0–1.0). Defaults to
		 * {@code 1.0} (100 % control).
		 */
		private double controlWeight = 1.0;

		/** Value returned when the control arm is selected. */
		private String controlValue;

		/** Value returned when the treatment arm is selected. */
		private String treatmentValue;

		/**
		 * Optional list of suggestion types (e.g. {@code RAG_QUERY}) for which this
		 * experiment is applicable. Empty list means it applies to all contexts.
		 */
		private List<String> appliesTo = new ArrayList<>();

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public double getControlWeight() {
			return controlWeight;
		}

		public void setControlWeight(double controlWeight) {
			this.controlWeight = controlWeight;
		}

		public String getControlValue() {
			return controlValue;
		}

		public void setControlValue(String controlValue) {
			this.controlValue = controlValue;
		}

		public String getTreatmentValue() {
			return treatmentValue;
		}

		public void setTreatmentValue(String treatmentValue) {
			this.treatmentValue = treatmentValue;
		}

		public List<String> getAppliesTo() {
			return appliesTo;
		}

		public void setAppliesTo(List<String> appliesTo) {
			this.appliesTo = appliesTo;
		}
	}
}
