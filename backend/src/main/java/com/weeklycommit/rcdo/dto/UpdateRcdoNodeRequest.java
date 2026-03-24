package com.weeklycommit.rcdo.dto;

import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * All fields are optional; null means "do not change".
 */
public record UpdateRcdoNodeRequest(@Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
		String description, UUID ownerTeamId, UUID ownerUserId) {
}
