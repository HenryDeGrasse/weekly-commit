package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByRecipientUserIdAndReadOrderByCreatedAtDesc(UUID recipientUserId, boolean read);

	List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

	long countByRecipientUserIdAndRead(UUID recipientUserId, boolean read);
}
