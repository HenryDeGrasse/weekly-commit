package com.weeklycommit.carryforward.dto;

import com.weeklycommit.plan.dto.CommitResponse;

/**
 * Response after a successful carry-forward operation.
 *
 * @param newCommit
 *            the newly created commit in the target week
 * @param link
 *            the provenance link connecting source to target commit
 * @param postLockAdded
 *            true when the target plan was already LOCKED and the commit was
 *            added as a post-lock scope change
 */
public record CarryForwardResponse(CommitResponse newCommit, CarryForwardLinkResponse link, boolean postLockAdded) {
}
