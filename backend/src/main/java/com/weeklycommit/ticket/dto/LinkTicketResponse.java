package com.weeklycommit.ticket.dto;

import com.weeklycommit.plan.dto.CommitResponse;

/**
 * Response after linking a ticket to a commit.
 *
 * @param commit
 *            updated commit (with workItemId set)
 * @param rcdoWarning
 *            non-null when both commit and ticket carry different RCDO nodes;
 *            the caller should surface this to the user
 */
public record LinkTicketResponse(CommitResponse commit, String rcdoWarning) {
}
