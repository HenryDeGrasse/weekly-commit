package com.weeklycommit.ai.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExperimentService}.
 *
 * <p>
 * Tests use a hand-built {@link ExperimentConfig} rather than Spring context so
 * they run fast with no I/O. The env-var path is tested by spying on
 * {@code readForceOverride}.
 */
class ExperimentServiceTest {

	// ── Helpers ──────────────────────────────────────────────────────────

	private static ExperimentConfig.ExperimentDefinition def(boolean enabled, double controlWeight, String control,
			String treatment, List<String> appliesTo) {
		ExperimentConfig.ExperimentDefinition d = new ExperimentConfig.ExperimentDefinition();
		d.setEnabled(enabled);
		d.setControlWeight(controlWeight);
		d.setControlValue(control);
		d.setTreatmentValue(treatment);
		d.setAppliesTo(appliesTo);
		return d;
	}

	private static ExperimentConfig configWith(Map<String, ExperimentConfig.ExperimentDefinition> entries) {
		ExperimentConfig cfg = new ExperimentConfig();
		cfg.setExperiments(entries);
		return cfg;
	}

	private static ExperimentService service(ExperimentConfig cfg) {
		return new ExperimentService(cfg);
	}

	// ── isEnabled ────────────────────────────────────────────────────────

	@Test
	void isEnabled_disabledExperiment_returnsFalse() {
		ExperimentConfig cfg = configWith(
				Map.of("llm-model", def(false, 1.0, "control-llm", "treatment-llm", List.of())));
		assertThat(service(cfg).isEnabled("llm-model")).isFalse();
	}

	@Test
	void isEnabled_nonExistentExperiment_returnsFalse() {
		ExperimentConfig cfg = configWith(Map.of());
		assertThat(service(cfg).isEnabled("does-not-exist")).isFalse();
	}

	@Test
	void isEnabled_enabledExperiment_returnsTrue() {
		ExperimentConfig cfg = configWith(Map.of("hyde", def(true, 1.0, "disabled", "enabled", List.of("RAG_QUERY"))));
		assertThat(service(cfg).isEnabled("hyde")).isTrue();
	}

	// ── assign: disabled experiment ──────────────────────────────────────

	@Test
	void assign_disabledExperiment_returnsControl() {
		ExperimentConfig cfg = configWith(
				Map.of("llm-model", def(false, 0.0, "control-llm", "treatment-llm", List.of())));
		ExperimentAssignment assignment = service(cfg).assign("llm-model", "user-1");
		assertThat(assignment.variant()).isEqualTo("control");
		assertThat(assignment.value()).isEqualTo("control-llm");
		assertThat(assignment.isControl()).isTrue();
		assertThat(assignment.isTreatment()).isFalse();
	}

	@Test
	void assign_unknownExperiment_returnsControlWithNullValue() {
		ExperimentConfig cfg = configWith(Map.of());
		ExperimentAssignment assignment = service(cfg).assign("unknown", "user-1");
		assertThat(assignment.variant()).isEqualTo("control");
		assertThat(assignment.value()).isNull();
	}

	// ── assign: controlWeight=1.0 always returns control ─────────────────

	@Test
	void assign_enabledWithControlWeight1_alwaysReturnsControl() {
		ExperimentConfig cfg = configWith(
				Map.of("llm-model", def(true, 1.0, "control-llm", "treatment-llm", List.of())));
		ExperimentService svc = service(cfg);
		// Run many times — controlWeight=1.0 must always land on control
		for (int i = 0; i < 200; i++) {
			ExperimentAssignment a = svc.assign("llm-model", "user-" + i);
			assertThat(a.variant()).as("iteration %d should be control", i).isEqualTo("control");
		}
	}

	// ── assign: controlWeight=0.0 always returns treatment ───────────────

	@Test
	void assign_enabledWithControlWeight0_alwaysReturnsTreatment() {
		ExperimentConfig cfg = configWith(
				Map.of("llm-model", def(true, 0.0, "control-llm", "treatment-llm", List.of())));
		ExperimentService svc = service(cfg);
		for (int i = 0; i < 200; i++) {
			ExperimentAssignment a = svc.assign("llm-model", "user-" + i);
			assertThat(a.variant()).as("iteration %d should be treatment", i).isEqualTo("treatment");
			assertThat(a.value()).isEqualTo("treatment-llm");
		}
	}

	// ── resolveValue ─────────────────────────────────────────────────────

	@Test
	void resolveValue_disabledExperiment_returnsControlValue() {
		ExperimentConfig cfg = configWith(Map.of("hyde", def(false, 1.0, "disabled", "enabled", List.of())));
		assertThat(service(cfg).resolveValue("hyde", "u1")).isEqualTo("disabled");
	}

	// ── env-var override ─────────────────────────────────────────────────

	@Test
	void assign_envVarForcesControl_returnsControlEvenWithControlWeight0() {
		ExperimentConfig cfg = configWith(Map.of("llm-model", def(true, 0.0, "ctrl-val", "treat-val", List.of())));
		ExperimentService svc = spy(service(cfg));
		doReturn("control").when(svc).readForceOverride("llm-model");

		ExperimentAssignment a = svc.assign("llm-model", "u1");
		assertThat(a.variant()).isEqualTo("control");
		assertThat(a.value()).isEqualTo("ctrl-val");
	}

	@Test
	void assign_envVarForcesTreatment_returnsTreatmentEvenWithControlWeight1() {
		ExperimentConfig cfg = configWith(Map.of("llm-model", def(true, 1.0, "ctrl-val", "treat-val", List.of())));
		ExperimentService svc = spy(service(cfg));
		doReturn("treatment").when(svc).readForceOverride("llm-model");

		ExperimentAssignment a = svc.assign("llm-model", "u1");
		assertThat(a.variant()).isEqualTo("treatment");
		assertThat(a.value()).isEqualTo("treat-val");
	}

	// ── getActiveAssignments ─────────────────────────────────────────────

	@Test
	void getActiveAssignments_onlyIncludesEnabledExperiments() {
		ExperimentConfig cfg = configWith(Map.of("llm-model", def(true, 1.0, "ctrl-llm", "treat-llm", List.of()),
				"hyde", def(false, 1.0, "disabled", "enabled", List.of("RAG_QUERY")), "embedding-model",
				def(false, 1.0, "small", "large", List.of())));
		Map<String, ExperimentAssignment> active = service(cfg).getActiveAssignments("u1");
		assertThat(active).containsOnlyKeys("llm-model");
		assertThat(active.get("llm-model").variant()).isEqualTo("control");
	}

	@Test
	void getActiveAssignments_noEnabledExperiments_returnsEmptyMap() {
		ExperimentConfig cfg = configWith(Map.of("llm-model", def(false, 1.0, "ctrl", "treat", List.of())));
		assertThat(service(cfg).getActiveAssignments("u1")).isEmpty();
	}

	@Test
	void getActiveAssignments_allEnabled_includesAll() {
		ExperimentConfig cfg = configWith(Map.of("llm-model", def(true, 1.0, "ctrl-llm", "treat-llm", List.of()),
				"hyde", def(true, 1.0, "disabled", "enabled", List.of("RAG_QUERY"))));
		Map<String, ExperimentAssignment> active = service(cfg).getActiveAssignments("u1");
		assertThat(active).containsKeys("llm-model", "hyde");
	}

	// ── readForceOverride ────────────────────────────────────────────────

	@Test
	void readForceOverride_noEnvVar_returnsNull() {
		// Real env lookup — key AB_FORCE_NONEXISTENT_EXPERIMENT is not set
		ExperimentService svc = service(configWith(Map.of()));
		assertThat(svc.readForceOverride("nonexistent-experiment")).isNull();
	}
}
