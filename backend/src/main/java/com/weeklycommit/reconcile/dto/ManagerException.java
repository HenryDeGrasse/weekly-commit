package com.weeklycommit.reconcile.dto;

/**
 * Represents a threshold-crossing event that requires manager attention.
 * Generated automatically when a scope change crosses a pre-defined rule.
 */
public record ManagerException(String type, String description) {

	/** King commit added or changed post-lock. */
	public static final String TYPE_KING_CHANGE = "KING_CHANGE";

	/**
	 * Total estimated points increased by more than 20 % relative to the baseline.
	 */
	public static final String TYPE_POINTS_INCREASE_20PCT = "POINTS_INCREASE_20PCT";
}
