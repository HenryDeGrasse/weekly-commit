package com.weeklycommit.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.AuditLog;
import com.weeklycommit.domain.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link AuditLogService}.
 *
 * <p>
 * Verifies that:
 * <ul>
 * <li>All event types produce audit records with correct fields.</li>
 * <li>System-action helper sets actorId=null and actorRole=SYSTEM.</li>
 * <li>Null payloads are handled without error.</li>
 * <li>A failure to serialize payload does not throw (graceful
 * degradation).</li>
 * <li>Query delegates to repository with correct specification.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

	@Mock
	private AuditLogRepository auditLogRepository;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private AuditLogService auditLogService;

	private final UUID actorId = UUID.randomUUID();
	private final UUID targetId = UUID.randomUUID();

	@BeforeEach
	void stubSave() {
		lenient().when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
			AuditLog a = inv.getArgument(0);
			if (a.getId() == null) {
				// simulate DB-generated UUID
			}
			return a;
		});
	}

	// =========================================================================
	// record() — core fields
	// =========================================================================

	@Test
	void record_persists_action_and_actor() {
		auditLogService.record(AuditLogService.PLAN_LOCKED, actorId, "IC", AuditLogService.TARGET_PLAN, targetId, null,
				null);

		ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		AuditLog saved = captor.getValue();

		assertThat(saved.getAction()).isEqualTo(AuditLogService.PLAN_LOCKED);
		assertThat(saved.getActorUserId()).isEqualTo(actorId);
		assertThat(saved.getActorRole()).isEqualTo("IC");
		assertThat(saved.getEntityType()).isEqualTo(AuditLogService.TARGET_PLAN);
		assertThat(saved.getEntityId()).isEqualTo(targetId);
	}

	@Test
	void record_serializes_before_payload() {
		Map<String, Object> before = Map.of("state", "DRAFT");

		auditLogService.record(AuditLogService.PLAN_LOCKED, actorId, "IC", AuditLogService.TARGET_PLAN, targetId,
				before, null);

		ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getOldValue()).contains("DRAFT");
	}

	@Test
	void record_serializes_after_payload() {
		Map<String, Object> after = Map.of("state", "LOCKED");

		auditLogService.record(AuditLogService.PLAN_LOCKED, actorId, "IC", AuditLogService.TARGET_PLAN, targetId, null,
				after);

		ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getNewValue()).contains("LOCKED");
	}

	@Test
	void record_stores_ip_address() {
		auditLogService.record(AuditLogService.TICKET_CREATED, actorId, "IC", AuditLogService.TARGET_TICKET, targetId,
				null, null, "10.0.0.1");

		ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getIpAddress()).isEqualTo("10.0.0.1");
	}

	@Test
	void record_null_payloads_are_stored_as_null() {
		auditLogService.record(AuditLogService.COMMIT_DELETED, actorId, "IC", AuditLogService.TARGET_COMMIT, targetId,
				null, null);

		ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getOldValue()).isNull();
		assertThat(captor.getValue().getNewValue()).isNull();
	}

	// =========================================================================
	// recordSystem() — system actor
	// =========================================================================

	@Test
	void recordSystem_sets_null_actor_and_system_role() {
		auditLogService.recordSystem(AuditLogService.PLAN_AUTO_LOCKED, AuditLogService.TARGET_PLAN, targetId, null,
				Map.of("state", "LOCKED"));

		ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		AuditLog saved = captor.getValue();

		assertThat(saved.getActorUserId()).isNull();
		assertThat(saved.getActorRole()).isEqualTo("SYSTEM");
		assertThat(saved.getAction()).isEqualTo(AuditLogService.PLAN_AUTO_LOCKED);
	}

	// =========================================================================
	// All canonical action constants
	// =========================================================================

	@Test
	void all_plan_lifecycle_actions_can_be_recorded() {
		String[] actions = {AuditLogService.PLAN_CREATED, AuditLogService.PLAN_LOCKED, AuditLogService.PLAN_AUTO_LOCKED,
				AuditLogService.RECONCILE_OPENED, AuditLogService.RECONCILE_SUBMITTED};

		for (String action : actions) {
			auditLogService.record(action, actorId, "IC", AuditLogService.TARGET_PLAN, targetId, null, null);
		}

		verify(auditLogRepository, times(actions.length)).save(any(AuditLog.class));
	}

	@Test
	void all_commit_actions_can_be_recorded() {
		String[] actions = {AuditLogService.COMMIT_CREATED, AuditLogService.COMMIT_UPDATED,
				AuditLogService.COMMIT_DELETED, AuditLogService.COMMIT_OUTCOME_SET,
				AuditLogService.COMMIT_ADDED_POST_LOCK, AuditLogService.COMMIT_EDITED_POST_LOCK,
				AuditLogService.COMMIT_REMOVED_POST_LOCK};

		for (String action : actions) {
			auditLogService.record(action, actorId, "IC", AuditLogService.TARGET_COMMIT, targetId, null, null);
		}

		verify(auditLogRepository, times(actions.length)).save(any(AuditLog.class));
	}

	@Test
	void all_ticket_actions_can_be_recorded() {
		String[] actions = {AuditLogService.TICKET_CREATED, AuditLogService.TICKET_UPDATED,
				AuditLogService.TICKET_DELETED, AuditLogService.TICKET_STATUS_CHANGED};

		for (String action : actions) {
			auditLogService.record(action, actorId, "IC", AuditLogService.TARGET_TICKET, targetId, null, null);
		}

		verify(auditLogRepository, times(actions.length)).save(any(AuditLog.class));
	}

	@Test
	void all_rcdo_actions_can_be_recorded() {
		String[] actions = {AuditLogService.RCDO_CREATED, AuditLogService.RCDO_UPDATED, AuditLogService.RCDO_ARCHIVED,
				AuditLogService.RCDO_MOVED};

		for (String action : actions) {
			auditLogService.record(action, actorId, "MANAGER", AuditLogService.TARGET_RCDO, targetId, null, null);
		}

		verify(auditLogRepository, times(actions.length)).save(any(AuditLog.class));
	}

	@Test
	void ai_and_config_actions_can_be_recorded() {
		String[] actions = {AuditLogService.AI_SUGGESTION_CREATED, AuditLogService.AI_FEEDBACK_SUBMITTED,
				AuditLogService.CONFIG_CHANGED, AuditLogService.CAPACITY_OVERRIDE_SET, AuditLogService.COMMENT_ADDED,
				AuditLogService.CARRY_FORWARD_CREATED};

		for (String action : actions) {
			auditLogService.record(action, actorId, "MANAGER", AuditLogService.TARGET_CONFIG, targetId, null, null);
		}

		verify(auditLogRepository, times(actions.length)).save(any(AuditLog.class));
	}

	// =========================================================================
	// query()
	// =========================================================================

	@Test
	void query_delegates_to_repository_with_sort() {
		when(auditLogRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

		List<AuditLog> result = auditLogService.query("PLAN", targetId, actorId, AuditLogService.PLAN_LOCKED,
				Instant.now().minusSeconds(3600), Instant.now());

		assertThat(result).isEmpty();
		verify(auditLogRepository).findAll(any(Specification.class), any(Sort.class));
	}

	@Test
	void query_with_all_null_filters_returns_all() {
		AuditLog entry = new AuditLog();
		entry.setAction(AuditLogService.PLAN_LOCKED);
		when(auditLogRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(entry));

		List<AuditLog> result = auditLogService.query(null, null, null, null, null, null);

		assertThat(result).hasSize(1);
	}

	// =========================================================================
	// Graceful degradation — repository failure must not propagate
	// =========================================================================

	@Test
	void record_repository_failure_does_not_throw() {
		// REQUIRES_NEW means the inner transaction handles failure; since we're
		// not in a Spring context here we just verify the service catches exceptions.
		when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

		// Should not throw — swallows the error with a log.error
		auditLogService.record(AuditLogService.PLAN_LOCKED, actorId, "IC", AuditLogService.TARGET_PLAN, targetId, null,
				null);
	}
}
