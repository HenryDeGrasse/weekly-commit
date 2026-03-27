package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.AiSuggestion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	/**
	 * Average faithfulness score by suggestion type for scored suggestions since
	 * the given instant.
	 */
	@Query("SELECT s.suggestionType, AVG(s.evalFaithfulnessScore) " + "FROM AiSuggestion s "
			+ "WHERE s.evalScoredAt IS NOT NULL AND s.evalScoredAt >= :since " + "GROUP BY s.suggestionType")
	List<Object[]> avgFaithfulnessByType(@Param("since") Instant since);

	/**
	 * Acceptance rate by suggestion type: [type, accepted_count,
	 * total_feedback_count].
	 */
	@Query("SELECT s.suggestionType, " + "SUM(CASE WHEN s.accepted = true THEN 1 ELSE 0 END), "
			+ "SUM(CASE WHEN s.accepted IS NOT NULL OR s.dismissed IS NOT NULL THEN 1 ELSE 0 END) "
			+ "FROM AiSuggestion s " + "WHERE s.createdAt >= :since " + "GROUP BY s.suggestionType")
	List<Object[]> acceptanceRateByType(@Param("since") Instant since);
}
