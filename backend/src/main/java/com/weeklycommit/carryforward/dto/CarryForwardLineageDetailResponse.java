package com.weeklycommit.carryforward.dto;

import java.util.List;
import java.util.UUID;

public record CarryForwardLineageDetailResponse(UUID currentCommitId, List<CarryForwardNodeResponse> chain) {
}
