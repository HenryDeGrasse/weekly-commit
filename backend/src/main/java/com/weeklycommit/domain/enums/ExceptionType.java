package com.weeklycommit.domain.enums;

/**
 * Types of manager-review exceptions that can be raised against a weekly plan.
 */
public enum ExceptionType {
	/** Plan remained in DRAFT past the lock deadline (missed manual lock). */
	MISSED_LOCK,
	/** Plan was auto-locked by the system (possibly with validation errors). */
	AUTO_LOCKED,
	/** Plan passed the reconcile deadline without reaching RECONCILED state. */
	MISSED_RECONCILE,
	/** Total estimate points exceed the capacity budget for the plan owner. */
	OVER_BUDGET,
	/** At least one commit has a carry-forward streak ≥ 2 weeks. */
	REPEATED_CARRY_FORWARD,
	/** Total estimate points increased by more than 20 % from the lock baseline. */
	POST_LOCK_SCOPE_INCREASE,
	/** A King-level commit was added or changed after the plan was locked. */
	KING_CHANGED_POST_LOCK,
	/** More than 3 scope-change events were recorded for the plan after lock. */
	HIGH_SCOPE_VOLATILITY
}
