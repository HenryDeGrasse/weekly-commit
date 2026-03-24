package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.ManagerComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManagerCommentRepository extends JpaRepository<ManagerComment, UUID> {

	List<ManagerComment> findByPlanIdOrderByCreatedAtAsc(UUID planId);

	List<ManagerComment> findByCommitIdOrderByCreatedAtAsc(UUID commitId);

	List<ManagerComment> findByAuthorUserId(UUID authorUserId);
}
