package com.weeklycommit.domain.enums;

/** Severity level for a manager-review exception. */
public enum ExceptionSeverity {
	/** Highest urgency — requires immediate manager attention. */
	HIGH,
	/** Notable — should be reviewed before end of week. */
	MEDIUM,
	/** Informational — worth monitoring but not urgent. */
	LOW
}
