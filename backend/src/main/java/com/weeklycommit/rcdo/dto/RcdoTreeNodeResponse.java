package com.weeklycommit.rcdo.dto;

import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import java.util.List;
import java.util.UUID;

public record RcdoTreeNodeResponse(UUID id, RcdoNodeType nodeType, RcdoNodeStatus status, String title,
		List<RcdoTreeNodeResponse> children) {
}
