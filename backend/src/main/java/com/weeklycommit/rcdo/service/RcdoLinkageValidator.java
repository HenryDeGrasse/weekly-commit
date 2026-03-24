package com.weeklycommit.rcdo.service;

import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates whether a given RCDO node is a legal target for a commit link, per
 * PRD §15 linkage rules:
 *
 * <ul>
 * <li>Rally Cries are never valid direct commit targets.</li>
 * <li>An Outcome is valid when it is ACTIVE.</li>
 * <li>A Defining Objective is valid only when it is ACTIVE <em>and</em> has no
 * ACTIVE child Outcomes.</li>
 * </ul>
 */
@Component
public class RcdoLinkageValidator {

	private final RcdoNodeRepository nodeRepo;

	public RcdoLinkageValidator(RcdoNodeRepository nodeRepo) {
		this.nodeRepo = nodeRepo;
	}

	/**
	 * Throws {@link RcdoValidationException} if the node is not a valid commit
	 * target, or {@link ResourceNotFoundException} if the node does not exist.
	 */
	@Transactional(readOnly = true)
	public void validateCommitLinkage(UUID rcdoNodeId) {
		RcdoNode node = nodeRepo.findById(rcdoNodeId)
				.orElseThrow(() -> new ResourceNotFoundException("RCDO node not found: " + rcdoNodeId));

		if (node.getNodeType() == RcdoNodeType.RALLY_CRY) {
			throw new RcdoValidationException("Commits cannot link directly to a Rally Cry");
		}

		if (node.getStatus() != RcdoNodeStatus.ACTIVE) {
			throw new RcdoValidationException(
					"Commits can only link to ACTIVE RCDO nodes; node status is " + node.getStatus());
		}

		if (node.getNodeType() == RcdoNodeType.DEFINING_OBJECTIVE) {
			List<RcdoNode> activeOutcomes = nodeRepo.findByParentIdAndStatus(node.getId(), RcdoNodeStatus.ACTIVE);
			if (!activeOutcomes.isEmpty()) {
				throw new RcdoValidationException("Commits must link to an Outcome when active Outcomes exist "
						+ "under this Defining Objective");
			}
		}
	}

	/**
	 * Returns {@code true} when the node is a valid commit target, {@code false}
	 * for any validation failure (missing node included).
	 */
	@Transactional(readOnly = true)
	public boolean isValidCommitTarget(UUID rcdoNodeId) {
		try {
			validateCommitLinkage(rcdoNodeId);
			return true;
		} catch (RcdoValidationException | ResourceNotFoundException e) {
			return false;
		}
	}
}
