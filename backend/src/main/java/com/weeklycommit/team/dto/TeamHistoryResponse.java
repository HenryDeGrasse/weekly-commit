package com.weeklycommit.team.dto;

import java.util.List;
import java.util.UUID;

public record TeamHistoryResponse(UUID teamId, List<TeamWeekHistoryEntry> entries) {
}
