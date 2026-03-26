package com.weeklycommit.reconcile.service;

import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that opens reconciliation for LOCKED plans whose reconcile-open
 * deadline has passed.
 *
 * <p>
 * Each plan is processed in its own transaction (delegated to
 * {@link ReconciliationService#openReconciliation}) so a failure on one plan
 * does not prevent the others from being processed.
 *
 * <p>
 * The job is idempotent: plans already in RECONCILING state are skipped by the
 * service delegate.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AutoReconcileJob {

	private static final Logger log = LoggerFactory.getLogger(AutoReconcileJob.class);

	private final WeeklyPlanRepository planRepo;
	private final ReconciliationService reconciliationService;

	public AutoReconcileJob(WeeklyPlanRepository planRepo, ReconciliationService reconciliationService) {
		this.planRepo = planRepo;
		this.reconciliationService = reconciliationService;
	}

	/**
	 * Runs every minute. Finds LOCKED plans past their reconcile deadline and opens
	 * reconciliation for each.
	 */
	@Scheduled(cron = "0 * * * * *")
	public void openExpiredReconciliations() {
		Instant now = Instant.now();
		List<UUID> expired = planRepo.findByStateAndReconcileDeadlineBefore(PlanState.LOCKED, now).stream()
				.map(p -> p.getId()).collect(Collectors.toList());

		if (expired.isEmpty()) {
			return;
		}

		log.info("Auto-reconcile job: {} expired LOCKED plan(s) to open for reconciliation", expired.size());

		for (UUID planId : expired) {
			try {
				reconciliationService.openReconciliation(planId);
				log.info("Opened reconciliation for plan {}", planId);
			} catch (Exception ex) {
				log.error("Failed to open reconciliation for plan {}: {}", planId, ex.getMessage(), ex);
			}
		}
	}
}
