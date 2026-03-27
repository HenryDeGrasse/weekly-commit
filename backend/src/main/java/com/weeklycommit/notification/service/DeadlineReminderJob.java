package com.weeklycommit.notification.service;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.NotificationEvent;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that sends reminder notifications before lock and reconcile
 * deadlines (PRD §18).
 *
 * <p>
 * Runs every 15 minutes. Sends reminders when:
 * <ul>
 * <li>LOCK_DUE_REMINDER — plan is DRAFT and lock deadline is within 2
 * hours.</li>
 * <li>RECONCILIATION_DUE_REMINDER — plan is RECONCILING and reconcile deadline
 * is within 2 hours.</li>
 * </ul>
 *
 * <p>
 * {@link NotificationService} enforces frequency limits (max 2 per user per
 * task per day), so this job can safely run multiple times without spamming.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineReminderJob {

	private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);

	/** How far before the deadline to start sending reminders. */
	private static final long REMINDER_WINDOW_HOURS = 2;

	private final WeeklyPlanRepository planRepo;
	private final NotificationService notificationService;

	public DeadlineReminderJob(WeeklyPlanRepository planRepo, NotificationService notificationService) {
		this.planRepo = planRepo;
		this.notificationService = notificationService;
	}

	/**
	 * Checks for approaching lock and reconcile deadlines every 15 minutes.
	 */
	@Scheduled(cron = "0 */15 * * * *")
	public void sendDeadlineReminders() {
		Instant now = Instant.now();
		Instant windowEnd = now.plus(REMINDER_WINDOW_HOURS, ChronoUnit.HOURS);

		sendLockReminders(now, windowEnd);
		sendReconcileReminders(now, windowEnd);
	}

	private void sendLockReminders(Instant now, Instant windowEnd) {
		List<WeeklyPlan> plans = planRepo.findByStateAndLockDeadlineBetween(PlanState.DRAFT, now, windowEnd);
		if (plans.isEmpty())
			return;

		log.info("Deadline reminder: {} DRAFT plan(s) approaching lock deadline", plans.size());
		for (WeeklyPlan plan : plans) {
			try {
				long minutesLeft = ChronoUnit.MINUTES.between(now, plan.getLockDeadline());
				String timeLabel = minutesLeft > 60 ? (minutesLeft / 60) + " hours" : minutesLeft + " minutes";

				notificationService.createNotification(plan.getOwnerUserId(), NotificationEvent.LOCK_DUE_REMINDER,
						"Lock deadline approaching",
						"Your weekly plan for " + plan.getWeekStartDate() + " locks in " + timeLabel
								+ ". Review your commitments and lock before the deadline to avoid auto-lock.",
						plan.getId(), "PLAN");
			} catch (Exception ex) {
				log.warn("Failed to send lock reminder for plan {}: {}", plan.getId(), ex.getMessage());
			}
		}
	}

	private void sendReconcileReminders(Instant now, Instant windowEnd) {
		List<WeeklyPlan> plans = planRepo.findByStateAndReconcileDeadlineBetween(PlanState.RECONCILING, now, windowEnd);
		if (plans.isEmpty())
			return;

		log.info("Deadline reminder: {} RECONCILING plan(s) approaching reconcile deadline", plans.size());
		for (WeeklyPlan plan : plans) {
			try {
				long minutesLeft = ChronoUnit.MINUTES.between(now, plan.getReconcileDeadline());
				String timeLabel = minutesLeft > 60 ? (minutesLeft / 60) + " hours" : minutesLeft + " minutes";

				notificationService.createNotification(plan.getOwnerUserId(),
						NotificationEvent.RECONCILIATION_DUE_REMINDER, "Reconciliation deadline approaching",
						"Your weekly plan for " + plan.getWeekStartDate() + " must be reconciled in " + timeLabel
								+ ". Set outcomes and carry forward incomplete work before the deadline.",
						plan.getId(), "PLAN");
			} catch (Exception ex) {
				log.warn("Failed to send reconcile reminder for plan {}: {}", plan.getId(), ex.getMessage());
			}
		}
	}
}
