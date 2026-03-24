package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import java.time.LocalDate;
import java.util.UUID;

public record LinkedCommitEntry(UUID commitId, UUID planId, String commitTitle, ChessPiece chessPiece,
		Integer estimatePoints, LocalDate weekStartDate, CommitOutcome outcome) {
}
