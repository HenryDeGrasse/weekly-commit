package com.weeklycommit.domain.enums;

/** Organizational role assigned to a user account. */
public enum UserRole {
	/** Individual contributor — can only access own plans and commits. */
	IC,
	/**
	 * Manager — full detail for direct reports, aggregates for indirect reports.
	 */
	MANAGER,
	/** Administrator — unrestricted access to all data. */
	ADMIN
}
