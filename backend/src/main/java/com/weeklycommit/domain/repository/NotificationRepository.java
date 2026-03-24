package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByRecipientUserIdAndReadOrderByCreatedAtDesc(UUID recipientUserId, boolean read);

	List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

	Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

	Page<Notification> findByRecipientUserIdAndReadOrderByCreatedAtDesc(UUID recipientUserId, boolean read,
			Pageable pageable);

	long countByRecipientUserIdAndRead(UUID recipientUserId, boolean read);

	/**
	 * Frequency check: count notifications for a given user, event type, and
	 * reference entity created on or after {@code startOfDay}.
	 */
	@Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientUserId = :userId"
			+ " AND n.notificationType = :type AND n.referenceId = :refId AND n.createdAt >= :startOfDay")
	long countTodayByUserTypeAndRef(@Param("userId") UUID userId, @Param("type") String type,
			@Param("refId") UUID refId, @Param("startOfDay") Instant startOfDay);

	/**
	 * Frequency check when there is no specific reference entity (matches any
	 * notification of the given type for the user today).
	 */
	@Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientUserId = :userId"
			+ " AND n.notificationType = :type AND n.createdAt >= :startOfDay")
	long countTodayByUserAndType(@Param("userId") UUID userId, @Param("type") String type,
			@Param("startOfDay") Instant startOfDay);

	/** All unread notifications of a given priority for digest assembly. */
	List<Notification> findByRecipientUserIdAndPriorityAndReadOrderByCreatedAtAsc(UUID recipientUserId, String priority,
			boolean read);

	/** Bulk mark-all-read for a user. */
	@Modifying
	@Query("UPDATE Notification n SET n.read = true WHERE n.recipientUserId = :userId AND n.read = false")
	int markAllReadForUser(@Param("userId") UUID userId);
}
