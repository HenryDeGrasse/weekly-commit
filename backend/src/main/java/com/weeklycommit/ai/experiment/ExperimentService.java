package com.weeklycommit.ai.experiment;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lightweight A/B experiment assignment service.
 *
 * <p>
 * Variant assignment follows this priority order:
 * <ol>
 * <li>Environment-variable override:
 * {@code AB_FORCE_<EXPERIMENT_NAME_UPPER>=control|treatment} (useful for
 * testing and canary deploys).</li>
 * <li>Deterministic hash: assigns control if the hash of
 * {@code experimentName + ":" + userId} falls below {@code controlWeight},
 * otherwise treatment.</li>
 * </ol>
 *
 * <p>
 * All experiments default to disabled and {@code controlWeight=1.0}, so
 * behaviour is unchanged unless an experiment is explicitly enabled.
 */
@Service
public class ExperimentService {

	private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

	static final String VARIANT_CONTROL = "control";
	static final String VARIANT_TREATMENT = "treatment";

	private final ExperimentConfig config;

	public ExperimentService(ExperimentConfig config) {
		this.config = config;
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Returns whether the named experiment is configured and enabled.
	 *
	 * @param experimentName
	 *            the experiment key (e.g. {@code "llm-model"})
	 */
	public boolean isEnabled(String experimentName) {
		ExperimentConfig.ExperimentDefinition def = config.getExperiments().get(experimentName);
		return def != null && def.isEnabled();
	}

	/**
	 * Assigns a variant for the given experiment and user, returning the full
	 * {@link ExperimentAssignment}.
	 *
	 * <p>
	 * If the experiment is disabled or not found, returns a control assignment with
	 * a {@code null} value so callers can safely use
	 * {@link ExperimentAssignment#value()} without null-checks breaking the
	 * disabled-default contract.
	 *
	 * @param experimentName
	 *            the experiment key
	 * @param userId
	 *            opaque caller identity used for deterministic overrides (env-var
	 *            path ignores this)
	 */
	public ExperimentAssignment assign(String experimentName, String userId) {
		ExperimentConfig.ExperimentDefinition def = config.getExperiments().get(experimentName);

		if (def == null || !def.isEnabled()) {
			return new ExperimentAssignment(experimentName, VARIANT_CONTROL,
					def != null ? def.getControlValue() : null);
		}

		String forced = readForceOverride(experimentName);
		if (forced != null) {
			String value = VARIANT_TREATMENT.equals(forced) ? def.getTreatmentValue() : def.getControlValue();
			log.debug("Experiment '{}' forced to variant='{}' via env override", experimentName, forced);
			return new ExperimentAssignment(experimentName, forced, value);
		}

		// Deterministic hash-based assignment: the same user always gets the
		// same variant for a given experiment. UUID user-ID strings have high
		// entropy, so Java's polynomial hashCode is well-distributed here.
		String hashKey = experimentName + ":" + (userId != null ? userId : "");
		double bucket = (hashKey.hashCode() & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;
		String variant = (bucket < def.getControlWeight()) ? VARIANT_CONTROL : VARIANT_TREATMENT;
		String value = VARIANT_TREATMENT.equals(variant) ? def.getTreatmentValue() : def.getControlValue();
		log.debug("Experiment '{}' assigned variant='{}' for userId='{}' (bucket={})", experimentName, variant, userId,
				bucket);
		return new ExperimentAssignment(experimentName, variant, value);
	}

	/**
	 * Convenience method returning just the resolved value string.
	 *
	 * @param experimentName
	 *            the experiment key
	 * @param userId
	 *            opaque caller identity
	 * @return the control or treatment value, or {@code null} if the experiment is
	 *         not found
	 */
	public String resolveValue(String experimentName, String userId) {
		return assign(experimentName, userId).value();
	}

	/**
	 * Returns a snapshot of assignments for all <em>enabled</em> experiments for
	 * the given user. The map key is the experiment name; value is the
	 * {@link ExperimentAssignment}.
	 *
	 * @param userId
	 *            opaque caller identity
	 */
	public Map<String, ExperimentAssignment> getActiveAssignments(String userId) {
		Map<String, ExperimentAssignment> result = new LinkedHashMap<>();
		for (String name : config.getExperiments().keySet()) {
			if (isEnabled(name)) {
				result.put(name, assign(name, userId));
			}
		}
		return result;
	}

	// ── Internal helpers ─────────────────────────────────────────────────

	/**
	 * Reads an environment-variable force-override for the experiment.
	 *
	 * <p>
	 * Variable name: {@code AB_FORCE_<EXPERIMENT_NAME_UPPER>} where hyphens are
	 * replaced with underscores, e.g. {@code AB_FORCE_LLM_MODEL}.
	 *
	 * @return {@code "control"}, {@code "treatment"}, or {@code null} if not set
	 */
	String readForceOverride(String experimentName) {
		String envKey = "AB_FORCE_" + experimentName.toUpperCase().replace('-', '_');
		String raw = System.getenv(envKey);
		if (raw == null) {
			return null;
		}
		String normalised = raw.trim().toLowerCase();
		if (VARIANT_CONTROL.equals(normalised) || VARIANT_TREATMENT.equals(normalised)) {
			return normalised;
		}
		log.warn("Ignoring unrecognised AB_FORCE value '{}' for experiment '{}'", raw, experimentName);
		return null;
	}
}
