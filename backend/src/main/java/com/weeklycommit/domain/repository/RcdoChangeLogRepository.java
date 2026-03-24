package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.RcdoChangeLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RcdoChangeLogRepository extends JpaRepository<RcdoChangeLog, UUID> {

	List<RcdoChangeLog> findByRcdoNodeIdOrderByCreatedAtAsc(UUID rcdoNodeId);
}
