package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.RcdoWeekRollup;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RcdoWeekRollupRepository extends JpaRepository<RcdoWeekRollup, UUID> {

	Optional<RcdoWeekRollup> findByRcdoNodeIdAndWeekStart(UUID rcdoNodeId, LocalDate weekStart);

	List<RcdoWeekRollup> findByRcdoNodeIdAndWeekStartBetween(UUID rcdoNodeId, LocalDate from, LocalDate to);
}
