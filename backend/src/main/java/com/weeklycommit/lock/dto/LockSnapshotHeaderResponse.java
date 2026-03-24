package com.weeklycommit.lock.dto;

import com.weeklycommit.domain.entity.LockSnapshotHeader;
import java.time.Instant;
import java.util.UUID;

public record LockSnapshotHeaderResponse(UUID id, UUID planId, Instant lockedAt, boolean lockedBySystem,
		String snapshotPayload) {

	public static LockSnapshotHeaderResponse from(LockSnapshotHeader header) {
		return new LockSnapshotHeaderResponse(header.getId(), header.getPlanId(), header.getLockedAt(),
				header.isLockedBySystem(), header.getSnapshotPayload());
	}
}
