package com.weeklycommit.domain.enums;

/**
 * All notification event types supported by the Weekly Commit Module.
 *
 * <p>
 * Priority mapping (PRD §18):
 * <ul>
 * <li>{@link #AUTO_LOCK_OCCURRED}, {@link #MANAGER_EXCEPTION_DIGEST},
 * {@link #CRITICAL_TICKET_BLOCKED} — HIGH (immediate delivery)</li>
 * <li>{@link #LOCK_DUE_REMINDER}, {@link #RECONCILIATION_OPENED},
 * {@link #RECONCILIATION_DUE_REMINDER},
 * {@link #REPEATED_CARRY_FORWARD_REMINDER}, {@link #UNASSIGNED_TICKET_CREATED}
 * — MEDIUM (same-day digest)</li>
 * <li>{@link #DRAFT_WINDOW_OPENED} — LOW (daily digest)</li>
 * </ul>
 */
public enum NotificationEvent {

	/** A new draft planning window has opened for the week. */
	DRAFT_WINDOW_OPENED,

	/** Reminder that the plan lock deadline is approaching. */
	LOCK_DUE_REMINDER,

	/** The system automatically locked the plan because the deadline passed. */
	AUTO_LOCK_OCCURRED,

	/** Reconciliation has been opened for a locked plan. */
	RECONCILIATION_OPENED,

	/** Reminder that the reconciliation deadline is approaching. */
	RECONCILIATION_DUE_REMINDER,

	/** A commit has been carried forward for 2 or more consecutive weeks. */
	REPEATED_CARRY_FORWARD_REMINDER,

	/**
	 * Digest of team-level exceptions sent to the manager (King-level scope
	 * changes, capacity overruns, etc.).
	 */
	MANAGER_EXCEPTION_DIGEST,

	/** A ticket was created without an assignee. */
	UNASSIGNED_TICKET_CREATED,

	/** A ticket linked to a King or Queen commit has been blocked. */
	CRITICAL_TICKET_BLOCKED;

	/**
	 * Returns the default delivery priority for this event.
	 *
	 * @return "HIGH", "MEDIUM", or "LOW"
	 */
	public String defaultPriority() {
		return switch (this) {
			case AUTO_LOCK_OCCURRED, MANAGER_EXCEPTION_DIGEST, CRITICAL_TICKET_BLOCKED -> "HIGH";
			case LOCK_DUE_REMINDER, RECONCILIATION_OPENED, RECONCILIATION_DUE_REMINDER, REPEATED_CARRY_FORWARD_REMINDER,
					UNASSIGNED_TICKET_CREATED ->
				"MEDIUM";
			case DRAFT_WINDOW_OPENED -> "LOW";
		};
	}
}
