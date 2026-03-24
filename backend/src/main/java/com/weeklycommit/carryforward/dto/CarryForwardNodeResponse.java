package com.weeklycommit.carryforward.dto;

import com.weeklycommit.domain.enums.CommitOutcome;
import java.time.LocalDate;
import java.util.UUID;

public record CarryForwardNodeResponse(UUID commitId, UUID planId, LocalDate weekStartDate, String title,
		CommitOutcome outcome, int streak) {
}
