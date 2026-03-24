package com.weeklycommit.team.exception;

/**
 * Thrown when a caller attempts to access a resource they are not authorised to
 * view or modify.
 */
public class AccessDeniedException extends RuntimeException {

	public AccessDeniedException(String message) {
		super(message);
	}
}
