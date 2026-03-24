package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.ComplianceFact;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplianceFactRepository extends JpaRepository<ComplianceFact, UUID> {

	Optional<ComplianceFact> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

	List<ComplianceFact> findByUserIdInAndWeekStartBetween(List<UUID> userIds, LocalDate from, LocalDate to);
}
