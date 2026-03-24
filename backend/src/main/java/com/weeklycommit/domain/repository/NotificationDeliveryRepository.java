package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.NotificationDelivery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

	List<NotificationDelivery> findByNotificationId(UUID notificationId);
}
