package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.ScopeChangeEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScopeChangeEventRepository extends JpaRepository<ScopeChangeEvent, UUID> {

	List<ScopeChangeEvent> findByPlanIdOrderByCreatedAtAsc(UUID planId);

	List<ScopeChangeEvent> findByCommitId(UUID commitId);
}
