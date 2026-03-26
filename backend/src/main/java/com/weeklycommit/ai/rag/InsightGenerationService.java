package com.weeklycommit.ai.rag;

import java.util.UUID;

/**
 * Generates proactive AI insights for weekly plans.
 *
 * <p>
 * This class is implemented in step-7. The interface is declared here so that
 * {@link com.weeklycommit.lock.service.LockService} can hold an
 * {@code @Autowired(required=false)} reference without a compilation error.
 * Until step-7 is complete, Spring will inject {@code null} and all call-sites
 * guard with a null-check.
 */
public interface InsightGenerationService {

	/**
	 * Asynchronously generates personal insights for the plan identified by
	 * {@code planId}. Called after a plan is locked to trigger proactive
	 * summarisation.
	 *
	 * @param planId
	 *            the locked plan to analyse
	 */
	void generatePersonalInsightsAsync(UUID planId);
}
