package com.weeklycommit.ai.metrics;

import com.weeklycommit.ai.provider.OpenRouterAiProvider;
import com.weeklycommit.domain.repository.AiFeedbackRepository;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Exposes Prometheus gauges for AI quality metrics. Refreshes every 5 minutes
 * from derived database queries.
 *
 * <p>
 * Metrics exposed:
 * <ul>
 * <li>{@code weekly_commit_ai_faithfulness_score} — rolling 7-day average
 * faithfulness by suggestion type</li>
 * <li>{@code weekly_commit_ai_acceptance_rate} — accepted / (accepted +
 * dismissed) by suggestion type</li>
 * <li>{@code weekly_commit_ai_provider_available} — 1 if provider is up, 0 if
 * down</li>
 * <li>{@code weekly_commit_ai_tokens_total} — total tokens consumed</li>
 * <li>{@code weekly_commit_ai_requests_total} — total requests made</li>
 * </ul>
 */
@Component
public class AiQualityMetrics {

	private static final Logger log = LoggerFactory.getLogger(AiQualityMetrics.class);

	private final AiSuggestionRepository suggestionRepo;
	private final AiFeedbackRepository feedbackRepo;
	private final MeterRegistry registry;

	@Autowired(required = false)
	private OpenRouterAiProvider openRouterProvider;

	/** Cache of current metric values, refreshed by scheduled job. */
	private final Map<String, Double> faithfulnessCache = new ConcurrentHashMap<>();
	private final Map<String, Double> acceptanceCache = new ConcurrentHashMap<>();
	private final AtomicReference<Double> providerAvailable = new AtomicReference<>(0.0);

	public AiQualityMetrics(AiSuggestionRepository suggestionRepo, AiFeedbackRepository feedbackRepo,
			MeterRegistry registry) {
		this.suggestionRepo = suggestionRepo;
		this.feedbackRepo = feedbackRepo;
		this.registry = registry;

		// Register provider availability gauge
		Gauge.builder("weekly_commit_ai_provider_available", providerAvailable, AtomicReference::get)
				.description("AI provider availability (1=up, 0=down)").register(registry);

		// Register token/request counters as gauges reading from the provider
		if (openRouterProvider != null) {
			Gauge.builder("weekly_commit_ai_tokens_total", openRouterProvider, OpenRouterAiProvider::getTotalTokensUsed)
					.description("Total LLM tokens consumed").register(registry);
			Gauge.builder("weekly_commit_ai_requests_total", openRouterProvider, OpenRouterAiProvider::getTotalRequests)
					.description("Total LLM requests made").register(registry);
		}
	}

	/**
	 * Refreshes AI quality metrics every 5 minutes. Reads from the database and
	 * updates Prometheus gauge values.
	 */
	@Scheduled(fixedRate = 300_000, initialDelay = 30_000)
	public void refreshMetrics() {
		try {
			refreshProviderAvailability();
			refreshFaithfulnessScores();
			refreshAcceptanceRates();
		} catch (Exception e) {
			log.warn("AiQualityMetrics: refresh failed — {}", e.getMessage());
		}
	}

	private void refreshProviderAvailability() {
		if (openRouterProvider != null) {
			providerAvailable.set(openRouterProvider.isAvailable() ? 1.0 : 0.0);
		}
	}

	private void refreshFaithfulnessScores() {
		Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

		// Query avg faithfulness by suggestion type for last 7 days
		List<Object[]> rows = suggestionRepo.avgFaithfulnessByType(sevenDaysAgo);
		for (Object[] row : rows) {
			String type = (String) row[0];
			Double avgScore = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;

			String key = "faithfulness_" + type;
			if (!faithfulnessCache.containsKey(key)) {
				// Register gauge on first encounter
				final String typeLabel = type;
				Gauge.builder("weekly_commit_ai_faithfulness_score",
						() -> faithfulnessCache.getOrDefault("faithfulness_" + typeLabel, 0.0))
						.tags(Tags.of("suggestion_type", typeLabel))
						.description("Rolling 7-day average faithfulness score").register(registry);
			}
			faithfulnessCache.put(key, avgScore);
		}
	}

	private void refreshAcceptanceRates() {
		Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

		List<Object[]> rows = suggestionRepo.acceptanceRateByType(sevenDaysAgo);
		for (Object[] row : rows) {
			String type = (String) row[0];
			long accepted = row[1] != null ? ((Number) row[1]).longValue() : 0;
			long total = row[2] != null ? ((Number) row[2]).longValue() : 0;
			double rate = total > 0 ? (double) accepted / total : 0.0;

			String key = "acceptance_" + type;
			if (!acceptanceCache.containsKey(key)) {
				final String typeLabel = type;
				Gauge.builder("weekly_commit_ai_acceptance_rate",
						() -> acceptanceCache.getOrDefault("acceptance_" + typeLabel, 0.0))
						.tags(Tags.of("suggestion_type", typeLabel))
						.description("Rolling 7-day AI suggestion acceptance rate").register(registry);
			}
			acceptanceCache.put(key, rate);
		}
	}
}
