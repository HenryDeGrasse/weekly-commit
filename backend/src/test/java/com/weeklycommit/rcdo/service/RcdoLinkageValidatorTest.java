package com.weeklycommit.rcdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcdoLinkageValidatorTest {

	@Mock
	private RcdoNodeRepository nodeRepo;

	@InjectMocks
	private RcdoLinkageValidator validator;

	private RcdoNode node(UUID id, RcdoNodeType type, RcdoNodeStatus status) {
		RcdoNode n = new RcdoNode();
		n.setId(id);
		n.setNodeType(type);
		n.setStatus(status);
		n.setTitle("Test");
		return n;
	}

	@Test
	void activeOutcome_isValidTarget() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node(id, RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE)));
		// should not throw
		validator.validateCommitLinkage(id);
	}

	@Test
	void activeDO_noActiveOutcomes_isValidTarget() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id))
				.thenReturn(Optional.of(node(id, RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE)));
		when(nodeRepo.findByParentIdAndStatus(id, RcdoNodeStatus.ACTIVE)).thenReturn(List.of());
		// should not throw
		validator.validateCommitLinkage(id);
	}

	@Test
	void activeDO_withActiveOutcomes_throws() {
		UUID doId = UUID.randomUUID();
		UUID outcomeId = UUID.randomUUID();
		when(nodeRepo.findById(doId))
				.thenReturn(Optional.of(node(doId, RcdoNodeType.DEFINING_OBJECTIVE, RcdoNodeStatus.ACTIVE)));
		when(nodeRepo.findByParentIdAndStatus(doId, RcdoNodeStatus.ACTIVE))
				.thenReturn(List.of(node(outcomeId, RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE)));

		assertThatThrownBy(() -> validator.validateCommitLinkage(doId)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Outcome");
	}

	@Test
	void activeRallyCry_throws() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node(id, RcdoNodeType.RALLY_CRY, RcdoNodeStatus.ACTIVE)));

		assertThatThrownBy(() -> validator.validateCommitLinkage(id)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("Rally Cry");
	}

	@Test
	void archivedOutcome_throws() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node(id, RcdoNodeType.OUTCOME, RcdoNodeStatus.ARCHIVED)));

		assertThatThrownBy(() -> validator.validateCommitLinkage(id)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("ACTIVE");
	}

	@Test
	void draftOutcome_throws() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node(id, RcdoNodeType.OUTCOME, RcdoNodeStatus.DRAFT)));

		assertThatThrownBy(() -> validator.validateCommitLinkage(id)).isInstanceOf(RcdoValidationException.class)
				.hasMessageContaining("ACTIVE");
	}

	@Test
	void missingNode_throwsResourceNotFound() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> validator.validateCommitLinkage(id)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void isValidCommitTarget_returnsFalseForRallyCry() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node(id, RcdoNodeType.RALLY_CRY, RcdoNodeStatus.ACTIVE)));
		assertThat(validator.isValidCommitTarget(id)).isFalse();
	}

	@Test
	void isValidCommitTarget_returnsTrueForActiveOutcome() {
		UUID id = UUID.randomUUID();
		when(nodeRepo.findById(id)).thenReturn(Optional.of(node(id, RcdoNodeType.OUTCOME, RcdoNodeStatus.ACTIVE)));
		assertThat(validator.isValidCommitTarget(id)).isTrue();
	}
}
