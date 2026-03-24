package com.weeklycommit.rcdo.dto;

import java.util.List;

/**
 * A node together with its ordered ancestor path [root → … → parent].
 */
public record RcdoNodeWithPathResponse(RcdoNodeResponse node, List<RcdoNodeResponse> ancestorPath) {
}
