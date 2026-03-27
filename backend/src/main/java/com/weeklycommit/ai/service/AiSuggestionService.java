package com.weeklycommit.ai.service;

import com.weeklycommit.ai.dto.AiFeedbackRequest;
import com.weeklycommit.ai.eval.FaithfulnessEvaluator;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiFeedback;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.repository.AiFeedbackRepository;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles storage and retrieval of {@link AiSuggestion} records and
 * {@link AiFeedback} records.
 */
@Service
@Transactional
public class AiSuggestionService {

	private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

	private final AiSuggestionRepository suggestionRepo;
	private final AiFeedbackRepository feedbackRepo;

	@Autowired(required = false)
	private FaithfulnessEvaluator faithfulnessEvaluator;

	public AiSuggestionService(AiSuggestionRepository suggestionRepo, AiFeedbackRepository feedbackRepo) {
		this.suggestionRepo = suggestionRepo;
		this.feedbackRepo = feedbackRepo;
	}

	/**
	 * Persists an {@link AiSuggestion} record for audit and feedback purposes.
	 *
	 * @param suggestionType
	 *            type discriminator (see
	 *            {@link com.weeklycommit.ai.provider.AiContext} constants)
	 * @param userId
	 *            requesting user
	 * @param planId
	 *            associated plan, may be null
	 * @param commitId
	 *            associated commit, may be null
	 * @param contextString
	 *            the serialised context used to produce the suggestion
	 * @param result
	 *            the provider result
	 * @return the persisted {@link AiSuggestion}
	 */
	public AiSuggestion storeSuggestion(String suggestionType, UUID userId, UUID planId, UUID commitId,
			String contextString, AiSuggestionResult result) {
		AiSuggestion s = new AiSuggestion();
		s.setSuggestionType(suggestionType);
		s.setUserId(userId);
		s.setPlanId(planId);
		s.setCommitId(commitId);
		s.setPrompt(contextString != null ? contextString : "{}");
		s.setContextHash(hash(contextString));
		s.setRationale(result.rationale());
		s.setSuggestionPayload(result.payload());
		s.setModelVersion(result.modelVersion());
		s.setPromptVersion(result.promptVersion());
		AiSuggestion saved = suggestionRepo.save(s);

		// Async production eval sampling (Phase 4a)
		if (faithfulnessEvaluator != null) {
			faithfulnessEvaluator.maybeScopeAsync(saved.getId());
		}

		return saved;
	}

	/**
	 * Overloaded variant that additionally stamps {@code teamId} and
	 * {@code weekStartDate} on the persisted record. Intended for TEAM_INSIGHT and
	 * PERSONAL_INSIGHT rows that need to be queried by team + week.
	 *
	 * @param suggestionType
	 *            type discriminator
	 * @param userId
	 *            requesting user
	 * @param planId
	 *            associated plan, may be null
	 * @param commitId
	 *            associated commit, may be null
	 * @param contextString
	 *            the serialised context used to produce the suggestion
	 * @param result
	 *            the provider result
	 * @param teamId
	 *            team scope, may be null
	 * @param weekStartDate
	 *            ISO week start (Monday), may be null
	 * @return the persisted {@link AiSuggestion}
	 */
	public AiSuggestion storeSuggestion(String suggestionType, UUID userId, UUID planId, UUID commitId,
			String contextString, AiSuggestionResult result, UUID teamId, LocalDate weekStartDate) {
		AiSuggestion s = storeSuggestion(suggestionType, userId, planId, commitId, contextString, result);
		s.setTeamId(teamId);
		s.setWeekStartDate(weekStartDate);
		return suggestionRepo.save(s);
	}

	/**
	 * Records user feedback on a suggestion.
	 *
	 * @param request
	 *            the feedback request
	 * @return the saved {@link AiFeedback}
	 * @throws ResourceNotFoundException
	 *             if the suggestion does not exist
	 */
	public AiFeedback recordFeedback(AiFeedbackRequest request) {
		if (!suggestionRepo.existsById(request.suggestionId())) {
			throw new ResourceNotFoundException("AI suggestion not found: " + request.suggestionId());
		}

		AiFeedback feedback = new AiFeedback();
		feedback.setSuggestionId(request.suggestionId());
		feedback.setUserId(request.userId());
		feedback.setAccepted(request.action() != AiFeedbackRequest.FeedbackAction.DISMISSED);
		feedback.setFeedbackNotes(request.notes());
		AiFeedback saved = feedbackRepo.save(feedback);

		// Also update the suggestion's accepted/dismissed flags for quick reads
		suggestionRepo.findById(request.suggestionId()).ifPresent(s -> {
			switch (request.action()) {
				case ACCEPTED, EDITED -> s.setAccepted(true);
				case DISMISSED -> s.setDismissed(true);
			}
			suggestionRepo.save(s);
		});

		log.debug("Recorded AI feedback: suggestionId={}, action={}", request.suggestionId(), request.action());
		return saved;
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	static String hash(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		} catch (NoSuchAlgorithmException e) {
			log.warn("SHA-256 not available; using empty hash");
			return "";
		}
	}
}
