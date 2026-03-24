package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WeeklyCommit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyCommitRepository extends JpaRepository<WeeklyCommit, UUID> {

	List<WeeklyCommit> findByPlanIdOrderByPriorityOrder(UUID planId);

	List<WeeklyCommit> findByOwnerUserId(UUID ownerUserId);

	List<WeeklyCommit> findByRcdoNodeId(UUID rcdoNodeId);

	List<WeeklyCommit> findByWorkItemId(UUID workItemId);

	long countByPlanId(UUID planId);
}
