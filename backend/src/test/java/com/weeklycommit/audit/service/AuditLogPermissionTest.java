package com.weeklycommit.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.weeklycommit.audit.controller.AuditLogController;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/**
 * Verifies permission boundaries on the audit log query endpoint.
 *
 * <ul>
 * <li>ADMIN may query with any actorId filter (including null = all).</li>
 * <li>Non-admin (IC / MANAGER) always sees only their own records, regardless
 * of the requested actorId param.</li>
 * <li>Unknown callerId throws ResourceNotFoundException.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogPermissionTest {

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private UserAccountRepository userAccountRepository;

	@InjectMocks
	private AuditLogController auditLogController;

	// =========================================================================
	// ADMIN — unrestricted filter
	// =========================================================================

	@Test
	void admin_can_query_with_any_actor_filter() {
		UUID adminId = UUID.randomUUID();
		UUID targetActorId = UUID.randomUUID();

		UserAccount admin = new UserAccount();
		admin.setId(adminId);
		admin.setRole("ADMIN");

		when(userAccountRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(auditLogService.query(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		ResponseEntity<?> response = auditLogController.queryAuditLog(null, null, targetActorId, null, null, null,
				adminId);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		// Verify that the service was called (actorId is passed through for admin)
		org.mockito.Mockito.verify(auditLogService).query(null, null, targetActorId, null, null, null);
	}

	@Test
	void admin_with_null_actor_filter_sees_all_records() {
		UUID adminId = UUID.randomUUID();

		UserAccount admin = new UserAccount();
		admin.setId(adminId);
		admin.setRole("ADMIN");

		when(userAccountRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(auditLogService.query(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		auditLogController.queryAuditLog(null, null, null, null, null, null, adminId);

		// actorId=null means "all actors" for admin
		org.mockito.Mockito.verify(auditLogService).query(null, null, null, null, null, null);
	}

	// =========================================================================
	// Non-admin (IC) — scoped to own records
	// =========================================================================

	@Test
	void ic_actorId_param_is_ignored_own_id_used_instead() {
		UUID icId = UUID.randomUUID();
		UUID someoneElseId = UUID.randomUUID();

		UserAccount ic = new UserAccount();
		ic.setId(icId);
		ic.setRole("IC");

		when(userAccountRepository.findById(icId)).thenReturn(Optional.of(ic));
		when(auditLogService.query(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		auditLogController.queryAuditLog(null, null, someoneElseId, null, null, null, icId);

		// Must use icId, not someoneElseId
		org.mockito.Mockito.verify(auditLogService).query(null, null, icId, null, null, null);
	}

	@Test
	void manager_actorId_param_is_ignored_own_id_used_instead() {
		UUID managerId = UUID.randomUUID();

		UserAccount manager = new UserAccount();
		manager.setId(managerId);
		manager.setRole("MANAGER");

		when(userAccountRepository.findById(managerId)).thenReturn(Optional.of(manager));
		when(auditLogService.query(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		auditLogController.queryAuditLog(null, null, UUID.randomUUID(), null, null, null, managerId);

		org.mockito.Mockito.verify(auditLogService).query(null, null, managerId, null, null, null);
	}

	// =========================================================================
	// No caller header
	// =========================================================================

	@Test
	void null_caller_returns_bad_request() {
		ResponseEntity<?> response = auditLogController.queryAuditLog(null, null, UUID.randomUUID(), null, null, null,
				null);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		org.mockito.Mockito.verifyNoInteractions(auditLogService);
	}

	// =========================================================================
	// Unknown caller
	// =========================================================================

	@Test
	void unknown_caller_throws_resource_not_found() {
		UUID unknownId = UUID.randomUUID();
		when(userAccountRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> auditLogController.queryAuditLog(null, null, null, null, null, null, unknownId))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
