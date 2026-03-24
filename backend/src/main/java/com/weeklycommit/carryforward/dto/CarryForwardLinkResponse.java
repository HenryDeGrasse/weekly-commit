package com.weeklycommit.carryforward.dto;

import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.enums.CarryForwardReason;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of a single carry-forward lineage link.
 */
public record CarryForwardLinkResponse(UUID id, UUID sourceCommitId, UUID targetCommitId, CarryForwardReason reason,
		String reasonText, Instant createdAt) {

	public static CarryForwardLinkResponse from(CarryForwardLink link) {
		return new CarryForwardLinkResponse(link.getId(), link.getSourceCommitId(), link.getTargetCommitId(),
				link.getReason(), link.getReasonNotes(), link.getCreatedAt());
	}
}
