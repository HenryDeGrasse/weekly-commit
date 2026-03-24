package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkItemStatusHistoryRepository extends JpaRepository<WorkItemStatusHistory, UUID> {

	List<WorkItemStatusHistory> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
}
