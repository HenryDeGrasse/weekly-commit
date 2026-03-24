package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

	Optional<WorkItem> findByTeamIdAndKey(UUID teamId, String key);

	List<WorkItem> findByAssigneeUserId(UUID assigneeUserId);

	List<WorkItem> findByTeamIdAndTargetWeekStartDate(UUID teamId, LocalDate targetWeekStartDate);

	List<WorkItem> findByTeamIdAndStatus(UUID teamId, TicketStatus status);
}
