package com.weeklycommit.rcdo.service;

import com.weeklycommit.domain.entity.RcdoChangeLog;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import com.weeklycommit.domain.repository.RcdoChangeLogRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.rcdo.dto.CreateRcdoNodeRequest;
import com.weeklycommit.rcdo.dto.RcdoNodeWithPathResponse;
import com.weeklycommit.rcdo.dto.RcdoNodeResponse;
import com.weeklycommit.rcdo.dto.RcdoTreeNodeResponse;
import com.weeklycommit.rcdo.dto.UpdateRcdoNodeRequest;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RcdoService {

	private final RcdoNodeRepository nodeRepo;
	private final RcdoChangeLogRepository changeLogRepo;

	public RcdoService(RcdoNodeRepository nodeRepo, RcdoChangeLogRepository changeLogRepo) {
		this.nodeRepo = nodeRepo;
		this.changeLogRepo = changeLogRepo;
	}

	// -------------------------------------------------------------------------
	// Create
	// -------------------------------------------------------------------------

	public RcdoNode createNode(CreateRcdoNodeRequest req, UUID actorUserId) {
		validateParentForType(req.nodeType(), req.parentId());

		RcdoNode node = new RcdoNode();
		node.setNodeType(req.nodeType());
		node.setStatus(RcdoNodeStatus.DRAFT);
		node.setParentId(req.parentId());
		node.setTitle(req.title());
		node.setDescription(req.description());
		node.setOwnerTeamId(req.ownerTeamId());
		node.setOwnerUserId(req.ownerUserId());

		RcdoNode saved = nodeRepo.save(node);
		writeChangeLog(saved.getId(), actorUserId, "CREATED", null, jsonField("title", saved.getTitle()));
		return saved;
	}

	// -------------------------------------------------------------------------
	// Read
	// -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public RcdoNode getNode(UUID id) {
		return findById(id);
	}

	@Transactional(readOnly = true)
	public RcdoNodeWithPathResponse getNodeWithPath(UUID id) {
		RcdoNode node = findById(id);
		List<RcdoNodeResponse> path = buildAncestorPath(node);
		return new RcdoNodeWithPathResponse(RcdoNodeResponse.from(node), path);
	}

	@Transactional(readOnly = true)
	public List<RcdoNode> listNodes(RcdoNodeType type, RcdoNodeStatus status, UUID parentId, UUID ownerTeamId) {
		return nodeRepo.findAll().stream().filter(n -> type == null || n.getNodeType() == type)
				.filter(n -> status == null || n.getStatus() == status)
				.filter(n -> parentId == null || Objects.equals(n.getParentId(), parentId))
				.filter(n -> ownerTeamId == null || Objects.equals(n.getOwnerTeamId(), ownerTeamId)).toList();
	}

	@Transactional(readOnly = true)
	public List<RcdoTreeNodeResponse> getTree() {
		List<RcdoNode> all = nodeRepo.findAll();
		return buildTree(all, null);
	}

	// -------------------------------------------------------------------------
	// Update
	// -------------------------------------------------------------------------

	public RcdoNode updateNode(UUID id, UpdateRcdoNodeRequest req, UUID actorUserId) {
		RcdoNode node = findById(id);
		StringBuilder changes = new StringBuilder("{");

		if (req.title() != null && req.title().isBlank()) {
			throw new RcdoValidationException("Title must not be blank");
		}

		if (req.title() != null && !req.title().equals(node.getTitle())) {
			changes.append("\"title\":\"").append(req.title()).append("\",");
			node.setTitle(req.title());
		}
		if (req.description() != null) {
			node.setDescription(req.description());
		}
		if (req.ownerTeamId() != null) {
			node.setOwnerTeamId(req.ownerTeamId());
		}
		if (req.ownerUserId() != null) {
			node.setOwnerUserId(req.ownerUserId());
		}

		RcdoNode saved = nodeRepo.save(node);
		String changesSummary = changes.append("}").toString();
		writeChangeLog(id, actorUserId, "UPDATED", null, changesSummary);
		return saved;
	}

	// -------------------------------------------------------------------------
	// Activate
	// -------------------------------------------------------------------------

	public RcdoNode activateNode(UUID id, UUID actorUserId) {
		RcdoNode node = findById(id);
		if (node.getStatus() == RcdoNodeStatus.ARCHIVED) {
			throw new RcdoValidationException("Cannot activate an archived node");
		}
		if (node.getStatus() == RcdoNodeStatus.ACTIVE) {
			return node; // already active, idempotent
		}
		String oldStatus = node.getStatus().name();
		node.setStatus(RcdoNodeStatus.ACTIVE);
		RcdoNode saved = nodeRepo.save(node);
		writeChangeLog(id, actorUserId, "ACTIVATED", jsonField("status", oldStatus), jsonField("status", "ACTIVE"));
		return saved;
	}

	// -------------------------------------------------------------------------
	// Archive
	// -------------------------------------------------------------------------

	public RcdoNode archiveNode(UUID id, UUID actorUserId) {
		RcdoNode node = findById(id);
		if (node.getStatus() == RcdoNodeStatus.ARCHIVED) {
			return node; // idempotent
		}

		List<RcdoNode> children = nodeRepo.findByParentId(id);
		boolean hasNonArchivedChildren = children.stream().anyMatch(c -> c.getStatus() != RcdoNodeStatus.ARCHIVED);
		if (hasNonArchivedChildren) {
			throw new RcdoValidationException(
					"Cannot archive node with non-archived children; archive or move them first");
		}

		String oldStatus = node.getStatus().name();
		node.setStatus(RcdoNodeStatus.ARCHIVED);
		RcdoNode saved = nodeRepo.save(node);
		writeChangeLog(id, actorUserId, "ARCHIVED", jsonField("status", oldStatus), jsonField("status", "ARCHIVED"));
		return saved;
	}

	// -------------------------------------------------------------------------
	// Move
	// -------------------------------------------------------------------------

	public RcdoNode moveNode(UUID id, UUID newParentId, UUID actorUserId) {
		RcdoNode node = findById(id);
		validateParentForType(node.getNodeType(), newParentId);

		UUID oldParentId = node.getParentId();
		node.setParentId(newParentId);
		RcdoNode saved = nodeRepo.save(node);
		writeChangeLog(id, actorUserId, "MOVED",
				jsonField("parentId", oldParentId == null ? "null" : oldParentId.toString()),
				jsonField("parentId", newParentId.toString()));
		return saved;
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private RcdoNode findById(UUID id) {
		return nodeRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("RCDO node not found: " + id));
	}

	/**
	 * Enforces the three-level hierarchy invariant:
	 * <ul>
	 * <li>RALLY_CRY — parentId must be null</li>
	 * <li>DEFINING_OBJECTIVE — parentId must reference a RALLY_CRY</li>
	 * <li>OUTCOME — parentId must reference a DEFINING_OBJECTIVE</li>
	 * </ul>
	 */
	void validateParentForType(RcdoNodeType type, UUID parentId) {
		switch (type) {
			case RALLY_CRY -> {
				if (parentId != null) {
					throw new RcdoValidationException("Rally Cry nodes must have no parent (top-level only)");
				}
			}
			case DEFINING_OBJECTIVE -> {
				if (parentId == null) {
					throw new RcdoValidationException("Defining Objective must have a Rally Cry parent");
				}
				RcdoNode parent = findById(parentId);
				if (parent.getNodeType() != RcdoNodeType.RALLY_CRY) {
					throw new RcdoValidationException(
							"Defining Objective parent must be a Rally Cry, got: " + parent.getNodeType());
				}
			}
			case OUTCOME -> {
				if (parentId == null) {
					throw new RcdoValidationException("Outcome must have a Defining Objective parent");
				}
				RcdoNode parent = findById(parentId);
				if (parent.getNodeType() != RcdoNodeType.DEFINING_OBJECTIVE) {
					throw new RcdoValidationException(
							"Outcome parent must be a Defining Objective, got: " + parent.getNodeType());
				}
			}
		}
	}

	private List<RcdoNodeResponse> buildAncestorPath(RcdoNode node) {
		List<RcdoNodeResponse> path = new ArrayList<>();
		UUID currentParentId = node.getParentId();
		while (currentParentId != null) {
			RcdoNode parent = findById(currentParentId);
			path.add(0, RcdoNodeResponse.from(parent));
			currentParentId = parent.getParentId();
		}
		return path;
	}

	private List<RcdoTreeNodeResponse> buildTree(List<RcdoNode> all, UUID parentId) {
		return all.stream().filter(n -> Objects.equals(n.getParentId(), parentId))
				.map(n -> new RcdoTreeNodeResponse(n.getId(), n.getNodeType(), n.getStatus(), n.getTitle(),
						buildTree(all, n.getId())))
				.toList();
	}

	private void writeChangeLog(UUID nodeId, UUID actorUserId, String summary, String previousValue, String newValue) {
		RcdoChangeLog log = new RcdoChangeLog();
		log.setRcdoNodeId(nodeId);
		log.setChangedByUserId(
				actorUserId != null ? actorUserId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
		log.setChangeSummary(summary);
		log.setPreviousValue(previousValue);
		log.setNewValue(newValue);
		changeLogRepo.save(log);
	}

	private static String jsonField(String key, String value) {
		return "{\"" + key + "\":\"" + value + "\"}";
	}
}
