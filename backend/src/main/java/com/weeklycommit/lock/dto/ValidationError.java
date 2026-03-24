package com.weeklycommit.lock.dto;

/** A single hard-validation failure reported during a lock attempt. */
public record ValidationError(String field, String message) {

	public static ValidationError of(String field, String message) {
		return new ValidationError(field, message);
	}
}
