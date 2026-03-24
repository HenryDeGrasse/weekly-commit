package com.weeklycommit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.domain.entity.Organization;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.RcdoNodeType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	// -------------------------------------------------------------------------
	// Organization
	// -------------------------------------------------------------------------

	@Test
	void organization_blankNameFails() {
		Organization org = new Organization();
		org.setName("");
		Set<ConstraintViolation<Organization>> violations = validator.validate(org);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
	}

	@Test
	void organization_validNamePasses() {
		Organization org = new Organization();
		org.setName("Acme Corp");
		Set<ConstraintViolation<Organization>> violations = validator.validate(org);
		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("name"));
	}

	// -------------------------------------------------------------------------
	// UserAccount
	// -------------------------------------------------------------------------

	@Test
	void userAccount_missingEmailFails() {
		UserAccount user = new UserAccount();
		user.setOrganizationId(UUID.randomUUID());
		user.setDisplayName("Alice");
		// email intentionally not set
		Set<ConstraintViolation<UserAccount>> violations = validator.validate(user);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
	}

	@Test
	void userAccount_invalidEmailFails() {
		UserAccount user = new UserAccount();
		user.setOrganizationId(UUID.randomUUID());
		user.setEmail("not-an-email");
		user.setDisplayName("Alice");
		Set<ConstraintViolation<UserAccount>> violations = validator.validate(user);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
	}

	// -------------------------------------------------------------------------
	// WeeklyPlan
	// -------------------------------------------------------------------------

	@Test
	void weeklyPlan_missingOwnerFails() {
		WeeklyPlan plan = new WeeklyPlan();
		plan.setTeamId(UUID.randomUUID());
		plan.setWeekStartDate(LocalDate.now());
		plan.setLockDeadline(Instant.now());
		plan.setReconcileDeadline(Instant.now());
		// ownerUserId intentionally not set
		Set<ConstraintViolation<WeeklyPlan>> violations = validator.validate(plan);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ownerUserId"));
	}

	@Test
	void weeklyPlan_validMinimalPlanPasses() {
		WeeklyPlan plan = new WeeklyPlan();
		plan.setOwnerUserId(UUID.randomUUID());
		plan.setTeamId(UUID.randomUUID());
		plan.setWeekStartDate(LocalDate.now());
		plan.setLockDeadline(Instant.now());
		plan.setReconcileDeadline(Instant.now());
		Set<ConstraintViolation<WeeklyPlan>> violations = validator.validate(plan);
		assertThat(violations).isEmpty();
	}

	// -------------------------------------------------------------------------
	// WeeklyCommit
	// -------------------------------------------------------------------------

	@Test
	void weeklyCommit_blankTitleFails() {
		WeeklyCommit commit = new WeeklyCommit();
		commit.setPlanId(UUID.randomUUID());
		commit.setOwnerUserId(UUID.randomUUID());
		commit.setTitle("");
		commit.setChessPiece(ChessPiece.PAWN);
		commit.setPriorityOrder(1);
		Set<ConstraintViolation<WeeklyCommit>> violations = validator.validate(commit);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
	}

	@Test
	void weeklyCommit_missingChessPieceFails() {
		WeeklyCommit commit = new WeeklyCommit();
		commit.setPlanId(UUID.randomUUID());
		commit.setOwnerUserId(UUID.randomUUID());
		commit.setTitle("Ship the feature");
		commit.setPriorityOrder(1);
		// chessPiece intentionally not set
		Set<ConstraintViolation<WeeklyCommit>> violations = validator.validate(commit);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("chessPiece"));
	}

	@Test
	void weeklyCommit_validCommitPasses() {
		WeeklyCommit commit = new WeeklyCommit();
		commit.setPlanId(UUID.randomUUID());
		commit.setOwnerUserId(UUID.randomUUID());
		commit.setTitle("Ship the feature");
		commit.setChessPiece(ChessPiece.ROOK);
		commit.setPriorityOrder(1);
		Set<ConstraintViolation<WeeklyCommit>> violations = validator.validate(commit);
		assertThat(violations).isEmpty();
	}

	// -------------------------------------------------------------------------
	// RcdoNode
	// -------------------------------------------------------------------------

	@Test
	void rcdoNode_blankTitleFails() {
		RcdoNode node = new RcdoNode();
		node.setNodeType(RcdoNodeType.OUTCOME);
		node.setTitle("");
		Set<ConstraintViolation<RcdoNode>> violations = validator.validate(node);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
	}

	@Test
	void rcdoNode_missingNodeTypeFails() {
		RcdoNode node = new RcdoNode();
		node.setTitle("Grow revenue");
		// nodeType intentionally not set
		Set<ConstraintViolation<RcdoNode>> violations = validator.validate(node);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("nodeType"));
	}

	@Test
	void rcdoNode_validNodePasses() {
		RcdoNode node = new RcdoNode();
		node.setNodeType(RcdoNodeType.OUTCOME);
		node.setTitle("Grow revenue by 20%");
		Set<ConstraintViolation<RcdoNode>> violations = validator.validate(node);
		assertThat(violations).isEmpty();
	}
}
