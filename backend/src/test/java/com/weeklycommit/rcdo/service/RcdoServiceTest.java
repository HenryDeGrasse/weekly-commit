package com.weeklycommit.rcdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.RcdoChangeLog;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import com.weeklycommit.domain.repository.RcdoChangeLogRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.rcdo.dto.CreateRcdoNodeRequest;
import com.weeklycommit.rcdo.dto.UpdateRcdoNodeRequest;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcdoServiceTest {

	@Mock
	private RcdoNodeRepository nodeRepo;

	@Mock
	private RcdoChangeLogRepository changeLogRepo;

	@InjectMocks
	private RcdoService service;

	private final UUID actorId = UUID.randomUUID();

	// ---------------------------------------------------------------------------
	// Shared test helpers
	// ---------------------------------------------------------------------------

	private RcdoNode nodeWith(RcdoNodeType type, RcdoNodeStatus status) {
		RcdoNode n = new RcdoNode();
		n.setId(UUID.randomUUID());
		n.setNodeType(type);
		n.setStatus(status);
		n.setTitle("Test " + type.name());
		return n;
	}

	private RcdoNode nodeWith(RcdoNodeType type, RcdoNodeStatus status, UUID parentId) {
		RcdoNode n = nodeWith(type, status);
		n.setParentId(parentId);
		return n;
	}

	@BeforeEach
	void stubSave() {
		// Use lenient() so tests that throw before reaching save() don't fail
		// with UnnecessaryStubbingException.
		lenient().when(nodeRepo.save(any(RcdoNode.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(changeLogRepo.save(any(RcdoChangeLog.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	// ---------------------------------------------------------------------------
	// createNode — hierarchy enforcement
	// ---------------------------------------------------------------------------

	@Test
	void createNode_rallyCry_noParent_succeeds() {
		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.RALLY_CRY, null, "RC1", null, null, null);
		RcdoNode result = service.createNode(req, actorId);
		assertThat(result.getNodeType()).isEqualTo(RcdoNodeType.RALLY_CRY);
		assertThat(result.getStatus()).isEqualTo(RcdoNodeStatus.DRAFT);
	}

	@Test
	void createNode_rallyCry_withParent_throws() {
		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.RALLY_CRY, UUID.randomUUID(), "RC1", null,
				null, null);
		assertThatThrownBy(() -> service.createNode(req, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Rally Cry");
	}

	@Test
	void createNode_definingObjective_withRallyCryParent_succeeds() {
		UUID rcId = UUID.randomUUID();
		RcdoNode rc = nodeWith(RcdoNodeType.RALLY_CRY, RcdoNodeStatus.ACTIVE);
		rc.setId(rcId);
		when(nodeRepo.findById(rcId)).thenReturn(Optional.of(rc));

		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.DEFINING_OBJECTIVE, rcId, "DO1", null, null,
				null);
		RcdoNode result = service.createNode(req, actorId);
		assertThat(result.getNodeType()).isEqualTo(RcdoNodeType.DEFINING_OBJECTIVE);
		assertThat(result.getParentId()).isEqualTo(rcId);
	}

	@Test
	void createNode_definingObjective_noParent_throws() {
		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.DEFINING_OBJECTIVE, null, "DO1", null, null,
				null);
		assertThatThrownBy(() -> service.createNode(req, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Defining Objective");
	}

	@Test
	void createNode_definingObjective_withOutcomeParent_throws() {
		UUID outcomeId = UUID.randomUUID();
		RcdoNode outcome = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE);
		outcome.setId(outcomeId);
		when(nodeRepo.findById(outcomeId)).thenReturn(Optional.of(outcome));

		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.DEFINING_OBJECTIVE, outcomeId, "DO1", null,
				null, null);
		assertThatThrownBy(() -> service.createNode(req, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Rally Cry");
	}

	@Test
	void createNode_outcome_withDefiningObjectiveParent_succeeds() {
		UUID doId = UUID.randomUUID();
		RcdoNode do1 = nodeWith(RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE);
		do1.setId(doId);
		when(nodeRepo.findById(doId)).thenReturn(Optional.of(do1));

		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.OUTCOME, doId, "OC1", null, null, null);
		RcdoNode result = service.createNode(req, actorId);
		assertThat(result.getNodeType()).isEqualTo(RcdoNodeType.OUTCOME);
		assertThat(result.getParentId()).isEqualTo(doId);
	}

	@Test
	void createNode_outcome_withRallyCryParent_throws() {
		UUID rcId = UUID.randomUUID();
		RcdoNode rc = nodeWith(RcdoNodeType.RALLY_CRY, RcdoNodeStatus.ACTIVE);
		rc.setId(rcId);
		when(nodeRepo.findById(rcId)).thenReturn(Optional.of(rc));

		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.OUTCOME, rcId, "OC1", null, null, null);
		assertThatThrownBy(() -> service.createNode(req, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Defining Objective");
	}

	// ---------------------------------------------------------------------------
	// activateNode — status transitions
	// ---------------------------------------------------------------------------

	@Test
	void activateNode_draftNode_becomesActive() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.DRAFT);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));

		RcdoNode result = service.activateNode(id, actorId);
		assertThat(result.getStatus()).isEqualTo(RcdoNodeStatus.ACTIVE);
	}

	@Test
	void activateNode_archivedNode_throws() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ARCHIVED);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));

		assertThatThrownBy(() -> service.activateNode(id, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("archived");
	}

	@Test
	void activateNode_alreadyActive_isIdempotent() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));

		RcdoNode result = service.activateNode(id, actorId);
		assertThat(result.getStatus()).isEqualTo(RcdoNodeStatus.ACTIVE);
		// save should NOT be called for an already-active node
		verify(nodeRepo, times(0)).save(node);
	}

	// ---------------------------------------------------------------------------
	// archiveNode — archival rules
	// ---------------------------------------------------------------------------

	@Test
	void archiveNode_noChildren_succeeds() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));
		when(nodeRepo.findByParentId(id)).thenReturn(List.of());

		RcdoNode result = service.archiveNode(id, actorId);
		assertThat(result.getStatus()).isEqualTo(RcdoNodeStatus.ARCHIVED);
	}

	@Test
	void archiveNode_allChildrenArchived_succeeds() {
		UUID id = UUID.randomUUID();
		RcdoNode parent = nodeWith(RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE);
		parent.setId(id);
		RcdoNode archivedChild = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ARCHIVED);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(parent));
		when(nodeRepo.findByParentId(id)).thenReturn(List.of(archivedChild));

		RcdoNode result = service.archiveNode(id, actorId);
		assertThat(result.getStatus()).isEqualTo(RcdoNodeStatus.ARCHIVED);
	}

	@Test
	void archiveNode_hasActiveChild_throws() {
		UUID id = UUID.randomUUID();
		RcdoNode parent = nodeWith(RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE);
		parent.setId(id);
		RcdoNode activeChild = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(parent));
		when(nodeRepo.findByParentId(id)).thenReturn(List.of(activeChild));

		assertThatThrownBy(() -> service.archiveNode(id, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("non-archived children");
	}

	@Test
	void archiveNode_hasDraftChild_throws() {
		UUID id = UUID.randomUUID();
		RcdoNode parent = nodeWith(RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE);
		parent.setId(id);
		RcdoNode draftChild = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.DRAFT);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(parent));
		when(nodeRepo.findByParentId(id)).thenReturn(List.of(draftChild));

		assertThatThrownBy(() -> service.archiveNode(id, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("non-archived children");
	}

	// ---------------------------------------------------------------------------
	// moveNode — re-parenting
	// ---------------------------------------------------------------------------

	@Test
	void moveNode_definingObjective_toAnotherRallyCry_succeeds() {
		UUID doId = UUID.randomUUID();
		UUID newRcId = UUID.randomUUID();

		RcdoNode doNode = nodeWith(RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE);
		doNode.setId(doId);
		RcdoNode newRc = nodeWith(RcdoNodeType.RALLY_CRY, RcdoNodeStatus.ACTIVE);
		newRc.setId(newRcId);

		when(nodeRepo.findById(doId)).thenReturn(Optional.of(doNode));
		when(nodeRepo.findById(newRcId)).thenReturn(Optional.of(newRc));

		RcdoNode result = service.moveNode(doId, newRcId, actorId);
		assertThat(result.getParentId()).isEqualTo(newRcId);
	}

	@Test
	void moveNode_definingObjective_toOutcomeParent_throws() {
		UUID doId = UUID.randomUUID();
		UUID outcomeId = UUID.randomUUID();

		RcdoNode doNode = nodeWith(RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE);
		doNode.setId(doId);
		RcdoNode outcome = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE);
		outcome.setId(outcomeId);

		when(nodeRepo.findById(doId)).thenReturn(Optional.of(doNode));
		when(nodeRepo.findById(outcomeId)).thenReturn(Optional.of(outcome));

		assertThatThrownBy(() -> service.moveNode(doId, outcomeId, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Rally Cry");
	}

	// ---------------------------------------------------------------------------
	// updateNode
	// ---------------------------------------------------------------------------

	@Test
	void updateNode_changesTitle() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.DRAFT);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));

		UpdateRcdoNodeRequest req = new UpdateRcdoNodeRequest("New Title", null, null, null);
		RcdoNode result = service.updateNode(id, req, actorId);
		assertThat(result.getTitle()).isEqualTo("New Title");
	}

	@Test
	void updateNode_blankTitle_throws() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.DRAFT);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));

		UpdateRcdoNodeRequest req = new UpdateRcdoNodeRequest("   ", null, null, null);
		assertThatThrownBy(() -> service.updateNode(id, req, actorId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Title must not be blank");
	}

	// ---------------------------------------------------------------------------
	// Change log
	// ---------------------------------------------------------------------------

	@Test
	void createNode_writesChangeLog() {
		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.RALLY_CRY, null, "RC1", null, null, null);
		service.createNode(req, actorId);
		verify(changeLogRepo, times(1)).save(any(RcdoChangeLog.class));
	}

	@Test
	void archiveNode_writesChangeLog() {
		UUID id = UUID.randomUUID();
		RcdoNode node = nodeWith(RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE);
		node.setId(id);
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node));
		when(nodeRepo.findByParentId(id)).thenReturn(List.of());

		service.archiveNode(id, actorId);
		verify(changeLogRepo, times(1)).save(any(RcdoChangeLog.class));
	}

	@Test
	void getNode_notFound_throws() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getNode(id)).isInstanceOf(ResourceNotFoundException.class);
	}
}
