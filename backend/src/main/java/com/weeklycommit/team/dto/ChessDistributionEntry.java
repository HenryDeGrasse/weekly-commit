package com.weeklycommit.team.dto;

import com.weeklycommit.domain.enums.ChessPiece;

/**
 * Count and total estimate points for a given chess piece across the team week.
 */
public record ChessDistributionEntry(ChessPiece chessPiece, int commitCount, int totalPoints) {
}
