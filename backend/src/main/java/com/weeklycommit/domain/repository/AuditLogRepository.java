package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

	List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

	List<AuditLog> findByActorUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID actorUserId, Instant after);
}
