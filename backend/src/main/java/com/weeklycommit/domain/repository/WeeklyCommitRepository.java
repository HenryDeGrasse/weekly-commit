package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WeeklyCommit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyCommitRepository extends JpaRepository<WeeklyCommit, UUID> {

	List<WeeklyCommit> findByPlanIdOrderByPriorityOrder(UUID planId);

	List<WeeklyCommit> findByOwnerUserId(UUID ownerUserId);

	List<WeeklyCommit> findByRcdoNodeId(UUID rcdoNodeId);

	List<WeeklyCommit> findByWorkItemId(UUID workItemId);

	long countByPlanId(UUID planId);

	/**
	 * Finds active (non-CANCELED) commits linked to the given ticket for the given
	 * assignee in a different commit (used for duplicate-link prevention within the
	 * same week).
	 */
	@Query("""
			SELECT c FROM WeeklyCommit c
			WHERE c.workItemId = :workItemId
			  AND c.ownerUserId = :ownerUserId
			  AND c.id <> :excludeCommitId
			  AND (c.outcome IS NULL OR c.outcome <> com.weeklycommit.domain.enums.CommitOutcome.CANCELED)
			""")
	List<WeeklyCommit> findActiveCommitsForTicketByOwnerExcluding(@Param("workItemId") UUID workItemId,
			@Param("ownerUserId") UUID ownerUserId, @Param("excludeCommitId") UUID excludeCommitId);
}
