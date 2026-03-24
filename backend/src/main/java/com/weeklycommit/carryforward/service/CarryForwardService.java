package com.weeklycommit.carryforward.service;

import com.weeklycommit.carryforward.dto.CarryForwardLineageDetailResponse;
import com.weeklycommit.carryforward.dto.CarryForwardLinkResponse;
import com.weeklycommit.carryforward.dto.CarryForwardLineageResponse;
import com.weeklycommit.carryforward.dto.CarryForwardNodeResponse;
import com.weeklycommit.carryforward.dto.CarryForwardResponse;
import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.CarryForwardReason;
import com.weeklycommit.domain.enums.NotificationEvent;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.CarryForwardLinkRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.notification.service.NotificationService;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.plan.service.WeeklyPlanService;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements PRD §13 FR-6 — carry-forward logic.
 *
 * <p>
 * A carry-forward copies a commit's core fields into a new commit in a target
 * week's plan, preserving provenance via a {@link CarryForwardLink}. The streak
 * counter tracks how many consecutive weeks a commit has been carried forward.
 */
@Service
@Transactional
public class CarryForwardService {

	private static final Logger log = LoggerFactory.getLogger(CarryForwardService.class);

	/** Streak threshold at which a repeated-carry-forward reminder is sent. */
	static final int CARRY_FORWARD_REMINDER_THRESHOLD = 2;

	private final WeeklyCommitRepository commitRepo;
	private final WeeklyPlanRepository planRepo;
	private final UserAccountRepository userRepo;
	private final CarryForwardLinkRepository linkRepo;
	private final ScopeChangeEventRepository scopeChangeEventRepo;
	private final NotificationService notificationService;

	public CarryForwardService(WeeklyCommitRepository commitRepo, WeeklyPlanRepository planRepo,
			UserAccountRepository userRepo, CarryForwardLinkRepository linkRepo,
			ScopeChangeEventRepository scopeChangeEventRepo, NotificationService notificationService) {
		this.commitRepo = commitRepo;
		this.planRepo = planRepo;
		this.userRepo = userRepo;
		this.linkRepo = linkRepo;
		this.scopeChangeEventRepo = scopeChangeEventRepo;
		this.notificationService = notificationService;
	}

	// -------------------------------------------------------------------------
	// carryForward
	// -------------------------------------------------------------------------

	/**
	 * Carries a commit forward into the target week's plan.
	 *
	 * <ul>
	 * <li>Copies title, description, chess piece, RCDO link, estimate points.</li>
	 * <li>Sets {@code carryForwardSourceId} and increments
	 * {@code carryForwardStreak} (source streak + 1).</li>
	 * <li>New commit is inserted at the bottom of the target plan's priority
	 * order.</li>
	 * <li>If the target week plan does not exist, it is lazily created.</li>
	 * <li>If the target plan is already LOCKED, the commit is added as a post-lock
	 * scope change event (COMMIT_ADDED with carry-forward reason).</li>
	 * </ul>
	 *
	 * @param sourceCommitId
	 *            commit to carry forward
	 * @param targetWeekStart
	 *            Monday of the target week
	 * @param reason
	 *            controlled enum reason
	 * @param reasonText
	 *            optional free-text elaboration
	 * @param actorUserId
	 *            user initiating the operation
	 * @return carry-forward response with new commit and provenance link
	 */
	public CarryForwardResponse carryForward(UUID planId, UUID sourceCommitId, LocalDate targetWeekStart,
			CarryForwardReason reason, String reasonText, UUID actorUserId) {

		WeeklyCommit source = requireCommit(sourceCommitId);
		validateCommitBelongsToPlan(planId, source);
		UUID ownerUserId = source.getOwnerUserId();

		// Get or lazily create the target week plan
		WeeklyPlan targetPlan = getOrCreateTargetPlan(ownerUserId, targetWeekStart);
		boolean postLockAdded = targetPlan.getState() == PlanState.LOCKED;

		// Build the new commit (provenance fields set regardless of plan state)
		WeeklyCommit newCommit = buildNewCommit(source, targetPlan);
		WeeklyCommit saved = commitRepo.save(newCommit);

		// If the target plan is LOCKED, record a scope-change event
		if (postLockAdded) {
			recordCarryForwardScopeChangeEvent(targetPlan.getId(), saved, reason, reasonText, actorUserId);
		}

		// Persist the provenance link
		CarryForwardLink link = new CarryForwardLink();
		link.setSourceCommitId(sourceCommitId);
		link.setTargetCommitId(saved.getId());
		link.setReason(reason);
		link.setReasonNotes(reasonText);
		CarryForwardLink savedLink = linkRepo.save(link);

		// Send REPEATED_CARRY_FORWARD_REMINDER if streak reaches the threshold
		if (saved.getCarryForwardStreak() >= CARRY_FORWARD_REMINDER_THRESHOLD) {
			try {
				notificationService.createNotification(ownerUserId, NotificationEvent.REPEATED_CARRY_FORWARD_REMINDER,
						"Repeated carry-forward detected",
						"\"" + saved.getTitle() + "\" has been carried forward for " + saved.getCarryForwardStreak()
								+ " consecutive week(s). Consider re-scoping or " + "blocking it as a dependency.",
						saved.getId(), "COMMIT");
			} catch (Exception ex) {
				log.warn("Failed to send carry-forward reminder for commit {}: {}", saved.getId(), ex.getMessage());
			}
		}

		return new CarryForwardResponse(CommitResponse.from(saved), CarryForwardLinkResponse.from(savedLink),
				postLockAdded);
	}

	// -------------------------------------------------------------------------
	// getCarryForwardLineage
	// -------------------------------------------------------------------------

	/**
	 * Returns the full carry-forward ancestry chain for {@code commitId}.
	 *
	 * <p>
	 * Traverses both ancestors (via {@code carryForwardSourceId} on the commit
	 * entity) and descendants (via {@link CarryForwardLinkRepository}) to produce a
	 * complete, ordered chain from the oldest ancestor to the youngest descendant.
	 *
	 * @param commitId
	 *            starting commit
	 * @return full lineage ordered oldest-first
	 */
	@Transactional(readOnly = true)
	public CarryForwardLineageResponse getCarryForwardLineage(UUID commitId) {
		requireCommit(commitId);

		List<CarryForwardLink> ancestors = new LinkedList<>();
		UUID cursor = commitId;
		while (true) {
			WeeklyCommit c = commitRepo.findById(cursor).orElse(null);
			if (c == null || c.getCarryForwardSourceId() == null) {
				break;
			}
			Optional<CarryForwardLink> link = linkRepo.findByTargetCommitId(cursor);
			link.ifPresent(l -> ancestors.add(0, l));
			cursor = c.getCarryForwardSourceId();
		}
		UUID rootCommitId = cursor;

		List<CarryForwardLink> descendants = new ArrayList<>();
		cursor = commitId;
		while (true) {
			List<CarryForwardLink> links = linkRepo.findBySourceCommitId(cursor);
			if (links.isEmpty()) {
				break;
			}
			CarryForwardLink next = links.get(0);
			descendants.add(next);
			cursor = next.getTargetCommitId();
		}

		List<CarryForwardLink> chain = new ArrayList<>();
		chain.addAll(ancestors);
		chain.addAll(descendants);

		List<CarryForwardLinkResponse> responses = chain.stream().map(CarryForwardLinkResponse::from).toList();
		return new CarryForwardLineageResponse(rootCommitId, responses);
	}

	@Transactional(readOnly = true)
	public CarryForwardLineageDetailResponse getCarryForwardLineageDetail(UUID commitId) {
		requireCommit(commitId);

		LinkedList<UUID> commitIds = new LinkedList<>();
		UUID cursor = commitId;
		while (true) {
			WeeklyCommit commit = requireCommit(cursor);
			commitIds.addFirst(commit.getId());
			if (commit.getCarryForwardSourceId() == null) {
				break;
			}
			cursor = commit.getCarryForwardSourceId();
		}

		cursor = commitId;
		while (true) {
			List<CarryForwardLink> links = linkRepo.findBySourceCommitId(cursor);
			if (links.isEmpty()) {
				break;
			}
			CarryForwardLink next = links.get(0);
			commitIds.addLast(next.getTargetCommitId());
			cursor = next.getTargetCommitId();
		}

		List<CarryForwardNodeResponse> chain = commitIds.stream().map(this::toCarryForwardNode).toList();
		return new CarryForwardLineageDetailResponse(commitId, chain);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit requireCommit(UUID commitId) {
		return commitRepo.findById(commitId)
				.orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + commitId));
	}

	private CarryForwardNodeResponse toCarryForwardNode(UUID commitId) {
		WeeklyCommit commit = requireCommit(commitId);
		WeeklyPlan plan = planRepo.findById(commit.getPlanId())
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + commit.getPlanId()));
		return new CarryForwardNodeResponse(commit.getId(), commit.getPlanId(), plan.getWeekStartDate(),
				commit.getTitle(), commit.getOutcome(), commit.getCarryForwardStreak());
	}

	private void validateCommitBelongsToPlan(UUID planId, WeeklyCommit commit) {
		if (!commit.getPlanId().equals(planId)) {
			throw new PlanValidationException("Commit " + commit.getId() + " does not belong to plan " + planId);
		}
	}

	/**
	 * Fetches an existing plan for the owner+week or lazily creates one. This
	 * mirrors the logic in {@link WeeklyPlanService} but is intentionally
	 * self-contained to avoid a circular service dependency.
	 */
	private WeeklyPlan getOrCreateTargetPlan(UUID ownerUserId, LocalDate targetWeekStart) {
		return planRepo.findByOwnerUserIdAndWeekStartDate(ownerUserId, targetWeekStart)
				.orElseGet(() -> createTargetPlan(ownerUserId, targetWeekStart));
	}

	private WeeklyPlan createTargetPlan(UUID ownerUserId, LocalDate weekStartDate) {
		UserAccount user = userRepo.findById(ownerUserId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));

		if (user.getHomeTeamId() == null) {
			throw new PlanValidationException(
					"User " + ownerUserId + " has no home team; cannot create a carry-forward plan");
		}

		WeeklyPlan plan = new WeeklyPlan();
		plan.setOwnerUserId(ownerUserId);
		plan.setTeamId(user.getHomeTeamId());
		plan.setWeekStartDate(weekStartDate);
		plan.setState(PlanState.DRAFT);
		plan.setCapacityBudgetPoints(user.getWeeklyCapacityPoints());
		plan.setLockDeadline(weekStartDate.atTime(12, 0).toInstant(ZoneOffset.UTC));
		plan.setReconcileDeadline(weekStartDate.plusDays(7).atTime(10, 0).toInstant(ZoneOffset.UTC));
		return planRepo.save(plan);
	}

	private WeeklyCommit buildNewCommit(WeeklyCommit source, WeeklyPlan targetPlan) {
		WeeklyCommit c = new WeeklyCommit();
		c.setPlanId(targetPlan.getId());
		c.setOwnerUserId(source.getOwnerUserId());
		c.setTitle(source.getTitle());
		c.setDescription(source.getDescription());
		c.setChessPiece(source.getChessPiece());
		c.setRcdoNodeId(source.getRcdoNodeId());
		c.setEstimatePoints(source.getEstimatePoints());
		c.setSuccessCriteria(source.getSuccessCriteria());
		// Provenance
		c.setCarryForwardSourceId(source.getId());
		c.setCarryForwardStreak(source.getCarryForwardStreak() + 1);
		// Bottom of priority order
		c.setPriorityOrder((int) (commitRepo.countByPlanId(targetPlan.getId()) + 1));
		return c;
	}

	/**
	 * Records a COMMIT_ADDED scope-change event when carry-forward targets a LOCKED
	 * plan.
	 */
	private void recordCarryForwardScopeChangeEvent(UUID planId, WeeklyCommit commit, CarryForwardReason reason,
			String reasonText, UUID actorUserId) {
		ScopeChangeEvent event = new ScopeChangeEvent();
		event.setPlanId(planId);
		event.setCommitId(commit.getId());
		event.setCategory(ScopeChangeCategory.COMMIT_ADDED);
		event.setChangedByUserId(
				actorUserId != null ? actorUserId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
		String reasonLabel = "CARRY_FORWARD:" + reason.name() + (reasonText != null ? " — " + reasonText : "");
		event.setReason(reasonLabel);
		event.setPreviousValue(null);
		// Minimal JSON payload matching what ScopeChangeService would produce
		event.setNewValue("{\"carryForward\":true,\"chessPiece\":\""
				+ (commit.getChessPiece() != null ? commit.getChessPiece().name() : "") + "\",\"title\":"
				+ quoteJson(commit.getTitle()) + "}");
		scopeChangeEventRepo.save(event);
	}

	private static String quoteJson(String s) {
		if (s == null) {
			return "null";
		}
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
