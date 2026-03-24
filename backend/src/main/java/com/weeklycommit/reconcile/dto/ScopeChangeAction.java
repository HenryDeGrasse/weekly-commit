package com.weeklycommit.reconcile.dto;

public enum ScopeChangeAction {
	/** Add a new commit to a locked plan. */
	ADD,
	/** Cancel/remove a baseline commit from a locked plan. */
	REMOVE,
	/** Edit mutable fields of a baseline commit after lock. */
	EDIT
}
