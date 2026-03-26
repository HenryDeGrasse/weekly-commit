package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.AiSuggestion;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {

	List<AiSuggestion> findByPlanId(UUID planId);

	List<AiSuggestion> findByCommitId(UUID commitId);

	List<AiSuggestion> findByPlanIdAndSuggestionType(UUID planId, String suggestionType);

	List<AiSuggestion> findByUserId(UUID userId);

	List<AiSuggestion> findByUserIdAndSuggestionType(UUID userId, String suggestionType);

	List<AiSuggestion> findByTeamIdAndWeekStartDateAndSuggestionType(UUID teamId, LocalDate weekStartDate,
			String suggestionType);
}
