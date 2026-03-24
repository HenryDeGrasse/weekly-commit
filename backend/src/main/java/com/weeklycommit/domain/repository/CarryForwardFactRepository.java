package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.CarryForwardFact;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarryForwardFactRepository extends JpaRepository<CarryForwardFact, UUID> {

	Optional<CarryForwardFact> findByCommitId(UUID commitId);

	List<CarryForwardFact> findByCurrentWeekBetween(LocalDate from, LocalDate to);
}
