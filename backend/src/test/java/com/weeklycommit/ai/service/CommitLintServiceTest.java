package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.weeklycommit.ai.dto.CommitLintRequest;
import com.weeklycommit.ai.dto.CommitLintResponse;
import com.weeklycommit.ai.dto.LintMessage;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommitLintServiceTest {

	@Mock
	private AiProviderRegistry registry;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private RcdoNodeRepository rcdoNodeRepo;

	private CommitLintService service;

	private UUID planId;
	private UUID userId;
	private WeeklyPlan plan;

	@BeforeEach
	void setUp() {
		service = new CommitLintService(registry, planRepo, commitRepo, rcdoNodeRepo);
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();

		plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(userId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 23));
		plan.setCapacityBudgetPoints(10);
	}

	// -------------------------------------------------------------------------
	// AI disabled
	// -------------------------------------------------------------------------

	@Test
	void lint_whenAiDisabled_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(false);

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.aiAvailable()).isFalse();
		assertThat(resp.hardValidation()).isEmpty();
		assertThat(resp.softGuidance()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Hard validation: missing success criteria for King/Queen
	// -------------------------------------------------------------------------

	@Test
	void lint_missingSuccessCriteriaForKing_isHardValidation() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		// rcdoNodeId is null — rcdoNodeRepo is NOT called for this commit
		WeeklyCommit commit = buildCommit("Implement auth module", ChessPiece.KING, null, null, null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.aiAvailable()).isTrue();
		List<String> hardCodes = resp.hardValidation().stream().map(LintMessage::code).toList();
		assertThat(hardCodes).contains("MISSING_SUCCESS_CRITERIA");
		assertThat(resp.softGuidance()).extracting(LintMessage::code).doesNotContain("MISSING_SUCCESS_CRITERIA");
	}

	@Test
	void lint_missingSuccessCriteriaForQueen_isHardValidation() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		// rcdoNodeId is null — rcdoNodeRepo is NOT called for this commit
		WeeklyCommit commit = buildCommit("Deliver user dashboard", ChessPiece.QUEEN, null, null, null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.hardValidation()).extracting(LintMessage::code).contains("MISSING_SUCCESS_CRITERIA");
	}

	@Test
	void lint_successCriteriaPresent_noHardError() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		// rcdoNodeId is null — rcdoNodeRepo is NOT called for this commit
		WeeklyCommit commit = buildCommit("Deliver the new feature", ChessPiece.KING, "Feature accepted by QA", null,
				null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.hardValidation()).extracting(LintMessage::code).doesNotContain("MISSING_SUCCESS_CRITERIA");
	}

	// -------------------------------------------------------------------------
	// Hard validation: duplicate titles
	// -------------------------------------------------------------------------

	@Test
	void lint_duplicateTitles_isHardValidation() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		// rcdoNodeId is null for both commits — rcdoNodeRepo is NOT called
		WeeklyCommit c1 = buildCommit("Fix the login bug properly", ChessPiece.ROOK, null, null, null);
		WeeklyCommit c2 = buildCommit("Fix the login bug properly", ChessPiece.ROOK, null, null, null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1, c2));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.hardValidation()).extracting(LintMessage::code).contains("DUPLICATE_TITLE");
	}

	// -------------------------------------------------------------------------
	// Soft guidance: vague title
	// -------------------------------------------------------------------------

	@Test
	void lint_vagueTitle_isSoftGuidance() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		// rcdoNodeId is null — rcdoNodeRepo is NOT called
		WeeklyCommit commit = buildCommit("misc", ChessPiece.PAWN, null, null, null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.softGuidance()).extracting(LintMessage::code).contains("VAGUE_TITLE");
		assertThat(resp.hardValidation()).extracting(LintMessage::code).doesNotContain("VAGUE_TITLE");
	}

	// -------------------------------------------------------------------------
	// Soft guidance: parent-level RCDO
	// -------------------------------------------------------------------------

	@Test
	void lint_parentLevelRcdo_isSoftGuidance() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		UUID rcdoId = UUID.randomUUID();
		WeeklyCommit commit = buildCommit("Implement search functionality", ChessPiece.ROOK, null, rcdoId, null);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));

		// Simulate parent node having active children — this stub IS used
		com.weeklycommit.domain.entity.RcdoNode childNode = new com.weeklycommit.domain.entity.RcdoNode();
		when(rcdoNodeRepo.findByParentIdAndStatus(rcdoId, RcdoNodeStatus.ACTIVE)).thenReturn(List.of(childNode));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.softGuidance()).extracting(LintMessage::code).contains("PARENT_LEVEL_RCDO");
	}

	@Test
	void lint_leafLevelRcdo_noSoftGuidance() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		UUID rcdoId = UUID.randomUUID();
		WeeklyCommit commit = buildCommit("Implement search functionality", ChessPiece.ROOK, null, rcdoId, null);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		// No active children → leaf node — this stub IS used
		when(rcdoNodeRepo.findByParentIdAndStatus(rcdoId, RcdoNodeStatus.ACTIVE)).thenReturn(List.of());
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.softGuidance()).extracting(LintMessage::code).doesNotContain("PARENT_LEVEL_RCDO");
	}

	// -------------------------------------------------------------------------
	// Soft guidance: estimate inconsistency
	// -------------------------------------------------------------------------

	@Test
	void lint_kingWithLowEstimate_isSoftGuidance() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		// rcdoNodeId is null — rcdoNodeRepo is NOT called
		WeeklyCommit commit = buildCommit("Deliver auth module completely", ChessPiece.KING, "All tests green", null,
				1);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, "{}", "ok", 0.9, "stub-v1"));

		CommitLintResponse resp = service.lint(new CommitLintRequest(planId, userId));

		assertThat(resp.softGuidance()).extracting(LintMessage::code).contains("ESTIMATE_INCONSISTENCY");
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit buildCommit(String title, ChessPiece chessPiece, String successCriteria, UUID rcdoNodeId,
			Integer estimatePoints) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(userId);
		c.setTitle(title);
		c.setChessPiece(chessPiece);
		c.setSuccessCriteria(successCriteria);
		c.setRcdoNodeId(rcdoNodeId);
		c.setEstimatePoints(estimatePoints);
		c.setPriorityOrder(1);
		return c;
	}
}
