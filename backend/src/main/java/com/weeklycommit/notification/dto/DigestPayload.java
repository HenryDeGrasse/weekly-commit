package com.weeklycommit.notification.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full digest payload prepared for a single user.
 *
 * @param userId
 *            recipient user id
 * @param generatedAt
 *            UTC timestamp when the digest was assembled
 * @param groups
 *            priority-grouped notification lists
 */
public record DigestPayload(UUID userId, Instant generatedAt, List<DigestGroup> groups) {
}
