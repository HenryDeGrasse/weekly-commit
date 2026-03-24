package com.weeklycommit.carryforward.dto;

import java.util.List;
import java.util.UUID;

/**
 * Full carry-forward ancestry chain for a given commit.
 *
 * <p>
 * {@code chain} is ordered from the oldest ancestor to the most recent
 * descendant. Each element represents one generation of carry-forward. The
 * queried commit may appear in the middle of the chain if it has both ancestors
 * and descendants.
 *
 * @param rootCommitId
 *            the oldest commit in the lineage (no carry-forward source)
 * @param chain
 *            ordered list of carry-forward links forming the ancestry
 */
public record CarryForwardLineageResponse(UUID rootCommitId, List<CarryForwardLinkResponse> chain) {
}
