package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.CapacityOverride;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CapacityOverrideRepository extends JpaRepository<CapacityOverride, UUID> {

	Optional<CapacityOverride> findByUserIdAndWeekStartDate(UUID userId, LocalDate weekStartDate);

	List<CapacityOverride> findByUserId(UUID userId);
}
