package com.weeklycommit.rcdo.dto;

import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import java.time.Instant;
import java.util.UUID;

public record RcdoNodeResponse(UUID id, RcdoNodeType nodeType, RcdoNodeStatus status, UUID parentId, String title,
		String description, UUID ownerTeamId, UUID ownerUserId, Instant createdAt, Instant updatedAt) {

	public static RcdoNodeResponse from(RcdoNode node) {
		return new RcdoNodeResponse(node.getId(), node.getNodeType(), node.getStatus(), node.getParentId(),
				node.getTitle(), node.getDescription(), node.getOwnerTeamId(), node.getOwnerUserId(),
				node.getCreatedAt(), node.getUpdatedAt());
	}
}
