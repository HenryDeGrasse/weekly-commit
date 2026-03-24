package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.TeamWeekRollup;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamWeekRollupRepository extends JpaRepository<TeamWeekRollup, UUID> {

	Optional<TeamWeekRollup> findByTeamIdAndWeekStart(UUID teamId, LocalDate weekStart);

	List<TeamWeekRollup> findByTeamIdAndWeekStartBetween(UUID teamId, LocalDate from, LocalDate to);
}
