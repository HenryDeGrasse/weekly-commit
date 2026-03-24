package com.weeklycommit.team.dto;

import com.weeklycommit.domain.entity.ManagerComment;
import java.time.Instant;
import java.util.UUID;

/** API representation of a manager comment. */
public record CommentResponse(UUID id, UUID planId, UUID commitId, UUID authorUserId, String content,
		Instant createdAt) {

	public static CommentResponse from(ManagerComment c) {
		return new CommentResponse(c.getId(), c.getPlanId(), c.getCommitId(), c.getAuthorUserId(), c.getContent(),
				c.getCreatedAt());
	}
}
