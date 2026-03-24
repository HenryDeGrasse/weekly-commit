package com.weeklycommit.lock.service;

import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that auto-locks DRAFT plans whose lock deadline has passed.
 *
 * <p>
 * Each plan is processed inside its own transaction (delegated to
 * {@link LockService#autoLockPlan}) so a failure on one plan does not prevent
 * others from being processed.
 *
 * <p>
 * The job is idempotent: if a plan has already been locked, the delegate method
 * is a no-op.
 */
@Component
public class AutoLockJob {

	private static final Logger log = LoggerFactory.getLogger(AutoLockJob.class);

	private final WeeklyPlanRepository planRepo;
	private final LockService lockService;

	public AutoLockJob(WeeklyPlanRepository planRepo, LockService lockService) {
		this.planRepo = planRepo;
		this.lockService = lockService;
	}

	/**
	 * Runs every minute. Finds DRAFT plans past their lock deadline and locks them.
	 * In production the cron expression would be tightened to match the configured
	 * lock cutoff window.
	 */
	@Scheduled(cron = "0 * * * * *")
	public void autoLockExpiredDrafts() {
		Instant now = Instant.now();
		java.util.List<UUID> expired = planRepo.findByStateAndLockDeadlineBefore(PlanState.DRAFT, now).stream()
				.map(p -> p.getId()).collect(Collectors.toList());

		if (expired.isEmpty())
			return;

		log.info("Auto-lock job: {} expired DRAFT plan(s) to process", expired.size());

		for (UUID planId : expired) {
			try {
				lockService.autoLockPlan(planId);
				log.info("Auto-locked plan {}", planId);
			} catch (Exception ex) {
				log.error("Auto-lock failed for plan {}: {}", planId, ex.getMessage(), ex);
			}
		}
	}
}
