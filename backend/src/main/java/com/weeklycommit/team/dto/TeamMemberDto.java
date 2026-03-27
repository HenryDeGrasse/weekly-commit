package com.weeklycommit.team.dto;

import java.util.UUID;

/**
 * Lightweight team member summary — id, display name, email, and membership
 * role. Used by the {@code GET /api/teams/{id}/members} endpoint so the
 * frontend can render assignee dropdowns without fetching the full
 * team-weekly-view payload.
 */
public record TeamMemberDto(UUID id, String displayName, String email, String role) {
}
