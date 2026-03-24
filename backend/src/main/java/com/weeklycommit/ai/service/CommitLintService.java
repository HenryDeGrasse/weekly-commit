package com.weeklycommit.ai.service;

import com.weeklycommit.ai.dto.CommitLintRequest;
import com.weeklycommit.ai.dto.CommitLintResponse;
import com.weeklycommit.ai.dto.LintMessage;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commit quality linting service (PRD §17 capability 2).
 *
 * <p>
 * Checks are split into:
 * <ul>
 * <li><b>hardValidation</b> — must be resolved before locking.</li>
 * <li><b>softGuidance</b> — informational only, never block locking.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CommitLintService {

	/** Titles consisting only of these generic words are considered vague. */
	private static final Set<String> VAGUE_WORDS = Set.of("fix", "update", "change", "misc", "todo", "wip", "work",
			"task", "thing", "stuff", "item", "various", "general", "other");

	/** A plan with more than this many commits is considered over-fragmented. */
	private static final int FRAGMENTATION_THRESHOLD = 12;

	/** The minimum meaningful title length. */
	private static final int MIN_TITLE_LENGTH = 10;

	private final AiProviderRegistry registry;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final RcdoNodeRepository rcdoNodeRepo;

	public CommitLintService(AiProviderRegistry registry, WeeklyPlanRepository planRepo,
			WeeklyCommitRepository commitRepo, RcdoNodeRepository rcdoNodeRepo) {
		this.registry = registry;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.rcdoNodeRepo = rcdoNodeRepo;
	}

	/**
	 * Runs commit quality checks for all commits in a plan.
	 *
	 * @param request
	 *            lint request with planId
	 * @return lint results categorised into hard and soft messages
	 */
	public CommitLintResponse lint(CommitLintRequest request) {
		if (!registry.isAiEnabled()) {
			return CommitLintResponse.unavailable();
		}

		WeeklyPlan plan = planRepo.findById(request.planId())
				.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.planId()));

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		List<LintMessage> hard = new ArrayList<>();
		List<LintMessage> soft = new ArrayList<>();

		// Plan-level checks
		if (commits.size() > FRAGMENTATION_THRESHOLD) {
			soft.add(new LintMessage("OVER_FRAGMENTED",
					"Plan has " + commits.size() + " commits — consider merging smaller items for focus.", null));
		}

		// Collect all titles for duplicate detection
		Map<String, UUID> seenTitles = new HashMap<>();

		for (WeeklyCommit commit : commits) {
			lintCommit(commit, hard, soft, seenTitles, plan);
		}

		// Store as ai_suggestion (rules-based lint, but stored for auditability)
		// We do not store the suggestion when AI is rule-only; the rules are
		// deterministic.
		// The AI provider is called for the soft-guidance enrichment part.
		enrichSoftGuidanceFromAi(request, commits, soft);

		return new CommitLintResponse(true, hard, soft);
	}

	// -------------------------------------------------------------------------
	// Per-commit checks
	// -------------------------------------------------------------------------

	private void lintCommit(WeeklyCommit commit, List<LintMessage> hard, List<LintMessage> soft,
			Map<String, UUID> seenTitles, WeeklyPlan plan) {

		String title = commit.getTitle() != null ? commit.getTitle().trim() : "";
		UUID id = commit.getId();

		// --- Hard: missing success criteria for King/Queen ---
		if ((commit.getChessPiece() == ChessPiece.KING || commit.getChessPiece() == ChessPiece.QUEEN)
				&& (commit.getSuccessCriteria() == null || commit.getSuccessCriteria().isBlank())) {
			hard.add(new LintMessage("MISSING_SUCCESS_CRITERIA",
					"Success criteria is required for " + commit.getChessPiece() + " commits.", id));
		}

		// --- Hard: duplicate / near-duplicate title ---
		String normalised = title.toLowerCase().replaceAll("\\s+", " ");
		if (seenTitles.containsKey(normalised)) {
			hard.add(new LintMessage("DUPLICATE_TITLE", "Duplicate commit title detected: \"" + title + "\".", id));
		} else {
			seenTitles.put(normalised, id);
		}

		// --- Soft: vague title ---
		if (title.length() < MIN_TITLE_LENGTH || isVagueTitle(title)) {
			soft.add(new LintMessage("VAGUE_TITLE",
					"Commit title \"" + title + "\" is vague or too short — be more specific.", id));
		}

		// --- Soft: parent-level RCDO when leaf node exists ---
		if (commit.getRcdoNodeId() != null) {
			boolean hasChildren = !rcdoNodeRepo.findByParentIdAndStatus(commit.getRcdoNodeId(), RcdoNodeStatus.ACTIVE)
					.isEmpty();
			if (hasChildren) {
				soft.add(new LintMessage("PARENT_LEVEL_RCDO",
						"Commit is linked to a parent RCDO node — consider linking to a leaf Outcome instead.", id));
			}
		}

		// --- Soft: estimate inconsistency (King/Queen with very small estimate) ---
		if ((commit.getChessPiece() == ChessPiece.KING || commit.getChessPiece() == ChessPiece.QUEEN)
				&& commit.getEstimatePoints() != null && commit.getEstimatePoints() <= 1) {
			soft.add(new LintMessage("ESTIMATE_INCONSISTENCY", "King/Queen commit has a very low estimate ("
					+ commit.getEstimatePoints() + " pt) — verify this reflects actual effort.", id));
		}
	}

	private boolean isVagueTitle(String title) {
		String[] words = title.toLowerCase().replaceAll("[^a-z\\s]", "").trim().split("\\s+");
		if (words.length == 0) {
			return true;
		}
		// All words are in the vague set → vague
		for (String word : words) {
			if (!VAGUE_WORDS.contains(word)) {
				return false;
			}
		}
		return true;
	}

	// -------------------------------------------------------------------------
	// AI enrichment for soft guidance
	// -------------------------------------------------------------------------

	/**
	 * Optionally calls the AI provider to enrich soft guidance. Failures are
	 * silently swallowed — lint always returns a result.
	 */
	private void enrichSoftGuidanceFromAi(CommitLintRequest request, List<WeeklyCommit> commits,
			List<LintMessage> soft) {
		try {
			Map<String, Object> planData = Map.of("commitCount", commits.size());
			AiContext ctx = new AiContext(AiContext.TYPE_COMMIT_LINT, request.userId(), request.planId(), null,
					Map.of(), planData, List.of(), List.of(), Map.of());
			registry.generateSuggestion(ctx);
			// We don't parse the AI output for lint — rules-based checks are authoritative.
			// AI call is made so providers can log/learn from the data.
		} catch (Exception ex) {
			// Swallow — lint never fails due to AI errors
		}
	}
}
