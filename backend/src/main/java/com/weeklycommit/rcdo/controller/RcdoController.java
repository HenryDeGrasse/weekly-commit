package com.weeklycommit.rcdo.controller;

import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import com.weeklycommit.rcdo.dto.CreateRcdoNodeRequest;
import com.weeklycommit.rcdo.dto.MoveRcdoNodeRequest;
import com.weeklycommit.rcdo.dto.RcdoNodeResponse;
import com.weeklycommit.rcdo.dto.RcdoNodeWithPathResponse;
import com.weeklycommit.rcdo.dto.RcdoTreeNodeResponse;
import com.weeklycommit.rcdo.dto.UpdateRcdoNodeRequest;
import com.weeklycommit.rcdo.service.RcdoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rcdo")
public class RcdoController {

	private final RcdoService rcdoService;

	public RcdoController(RcdoService rcdoService) {
		this.rcdoService = rcdoService;
	}

	/** Create a new RCDO node. Returns 201 with the created node. */
	@PostMapping("/nodes")
	public ResponseEntity<RcdoNodeResponse> createNode(@Valid @RequestBody CreateRcdoNodeRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		RcdoNode node = rcdoService.createNode(request, actorUserId);
		return ResponseEntity.status(HttpStatus.CREATED).body(RcdoNodeResponse.from(node));
	}

	/**
	 * List RCDO nodes, optionally filtered by type, status, parent, or ownerTeam.
	 */
	@GetMapping("/nodes")
	public ResponseEntity<List<RcdoNodeResponse>> listNodes(@RequestParam(required = false) RcdoNodeType type,
			@RequestParam(required = false) RcdoNodeStatus status, @RequestParam(required = false) UUID parentId,
			@RequestParam(required = false) UUID ownerTeamId) {
		List<RcdoNode> nodes = rcdoService.listNodes(type, status, parentId, ownerTeamId);
		return ResponseEntity.ok(nodes.stream().map(RcdoNodeResponse::from).toList());
	}

	/** Get a single node with its full ancestor path. */
	@GetMapping("/nodes/{id}")
	public ResponseEntity<RcdoNodeWithPathResponse> getNode(@PathVariable UUID id) {
		return ResponseEntity.ok(rcdoService.getNodeWithPath(id));
	}

	/** Update mutable fields of a node. Null fields in the request are ignored. */
	@PutMapping("/nodes/{id}")
	public ResponseEntity<RcdoNodeResponse> updateNode(@PathVariable UUID id,
			@Valid @RequestBody UpdateRcdoNodeRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		RcdoNode node = rcdoService.updateNode(id, request, actorUserId);
		return ResponseEntity.ok(RcdoNodeResponse.from(node));
	}

	/** Transition a DRAFT node to ACTIVE. */
	@PostMapping("/nodes/{id}/activate")
	public ResponseEntity<RcdoNodeResponse> activateNode(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		RcdoNode node = rcdoService.activateNode(id, actorUserId);
		return ResponseEntity.ok(RcdoNodeResponse.from(node));
	}

	/** Archive a node. Fails if the node has non-archived children. */
	@PostMapping("/nodes/{id}/archive")
	public ResponseEntity<RcdoNodeResponse> archiveNode(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		RcdoNode node = rcdoService.archiveNode(id, actorUserId);
		return ResponseEntity.ok(RcdoNodeResponse.from(node));
	}

	/** Re-parent a node within valid hierarchy constraints. */
	@PostMapping("/nodes/{id}/move")
	public ResponseEntity<RcdoNodeResponse> moveNode(@PathVariable UUID id,
			@Valid @RequestBody MoveRcdoNodeRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		RcdoNode node = rcdoService.moveNode(id, request.newParentId(), actorUserId);
		return ResponseEntity.ok(RcdoNodeResponse.from(node));
	}

	/** Return the full hierarchy tree for use in selection UIs. */
	@GetMapping("/tree")
	public ResponseEntity<List<RcdoTreeNodeResponse>> getTree() {
		return ResponseEntity.ok(rcdoService.getTree());
	}
}
