package com.weeklycommit.ai.provider;

import com.weeklycommit.ai.experiment.ExperimentAssignment;
import com.weeklycommit.ai.experiment.ExperimentService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manages registered {@link AiProvider} implementations and exposes the active
 * provider for the application.
 *
 * <p>
 * AI can be disabled globally via the {@code ai.enabled} property (default
 * {@code true}). When disabled, {@link #isAiEnabled()} returns {@code false}
 * and {@link #generateSuggestion(AiContext)} returns
 * {@link AiSuggestionResult#unavailable()} without calling any provider.
 *
 * <p>
 * Provider selection: the first registered provider that reports
 * {@link AiProvider#isAvailable()} is used. If none are available, the result
 * is also unavailable.
 */
@Component
public class AiProviderRegistry {

	private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

	@Value("${ai.enabled:true}")
	private boolean aiEnabled;

	private final List<AiProvider> providers;

	@Autowired(required = false)
	private ExperimentService experimentService;

	public AiProviderRegistry(List<AiProvider> providers) {
		this.providers = providers != null ? providers : List.of();
	}

	/**
	 * Returns {@code true} when AI is enabled via feature flag AND at least one
	 * provider is registered.
	 */
	public boolean isAiEnabled() {
		return aiEnabled && !providers.isEmpty();
	}

	/**
	 * Returns the first registered provider that reports itself as available, or
	 * {@link Optional#empty()} if none are.
	 */
	public Optional<AiProvider> getActiveProvider() {
		if (!aiEnabled) {
			log.debug("AI is disabled via feature flag");
			return Optional.empty();
		}
		return providers.stream().filter(AiProvider::isAvailable).findFirst();
	}

	/**
	 * Convenience method: generates a suggestion using the active provider, or
	 * returns {@link AiSuggestionResult#unavailable()} with graceful degradation if
	 * the provider is unavailable or AI is disabled.
	 *
	 * <p>
	 * Core workflow is never affected — callers should check
	 * {@link AiSuggestionResult#available()} on the response.
	 *
	 * @param context
	 *            the input context
	 * @return suggestion result, never {@code null}
	 */
	public AiSuggestionResult generateSuggestion(AiContext context) {
		Optional<AiProvider> provider = getActiveProvider();
		if (provider.isEmpty()) {
			log.debug("No active AI provider; returning unavailable result for type={}", context.suggestionType());
			return AiSuggestionResult.unavailable();
		}
		try {
			return provider.get().generateSuggestion(context);
		} catch (Exception ex) {
			log.warn("AI provider threw exception for type={}: {}", context.suggestionType(), ex.getMessage());
			return AiSuggestionResult.unavailable();
		}
	}

	/**
	 * Experiment-aware variant of {@link #generateSuggestion(AiContext)}.
	 *
	 * <p>
	 * When an {@link ExperimentService} is wired and the {@code llm-model}
	 * experiment is enabled, the active variant's model value is passed to the
	 * provider as a per-request override. The variant assignment is recorded in
	 * {@link AiSuggestionResult#experimentAssignments()} for audit/observability.
	 *
	 * <p>
	 * Falls back to the same graceful-degradation behaviour as the base overload.
	 *
	 * @param context
	 *            the input context
	 * @param userId
	 *            caller identity used for experiment assignment (may be
	 *            {@code null})
	 * @return suggestion result, never {@code null}
	 */
	public AiSuggestionResult generateSuggestion(AiContext context, UUID userId) {
		Optional<AiProvider> provider = getActiveProvider();
		if (provider.isEmpty()) {
			log.debug("No active AI provider; returning unavailable result for type={}", context.suggestionType());
			return AiSuggestionResult.unavailable();
		}

		String modelOverride = null;
		Map<String, String> assignments = Map.of();

		if (experimentService != null && experimentService.isEnabled("llm-model")) {
			String userKey = userId != null ? userId.toString() : "";
			ExperimentAssignment assignment = experimentService.assign("llm-model", userKey);
			assignments = Map.of("llm-model", assignment.variant());
			if (assignment.isTreatment() && assignment.value() != null) {
				modelOverride = assignment.value();
				log.debug("LLM A/B: using treatment model '{}' for userId={}", modelOverride, userId);
			}
		}

		try {
			AiSuggestionResult base = provider.get().generateSuggestion(context, modelOverride);
			if (!assignments.isEmpty()) {
				return new AiSuggestionResult(base.available(), base.payload(), base.rationale(), base.confidence(),
						base.modelVersion(), base.promptVersion(), assignments);
			}
			return base;
		} catch (Exception ex) {
			log.warn("AI provider threw exception for type={}: {}", context.suggestionType(), ex.getMessage());
			return AiSuggestionResult.unavailable();
		}
	}

	// Visible for testing
	boolean isAiEnabledFlag() {
		return aiEnabled;
	}

	void setAiEnabled(boolean aiEnabled) {
		this.aiEnabled = aiEnabled;
	}

	// Visible for testing
	void setExperimentService(ExperimentService experimentService) {
		this.experimentService = experimentService;
	}
}
