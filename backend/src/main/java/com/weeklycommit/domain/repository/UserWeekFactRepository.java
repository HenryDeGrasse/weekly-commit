package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.UserWeekFact;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserWeekFactRepository extends JpaRepository<UserWeekFact, UUID> {

	Optional<UserWeekFact> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

	List<UserWeekFact> findByUserIdInAndWeekStartBetween(List<UUID> userIds, LocalDate from, LocalDate to);

	List<UserWeekFact> findByUserIdAndWeekStartBetween(UUID userId, LocalDate from, LocalDate to);

	List<UserWeekFact> findByUserIdIn(List<UUID> userIds);
}
