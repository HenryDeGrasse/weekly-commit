package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.TeamMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {

	List<TeamMembership> findByTeamId(UUID teamId);

	List<TeamMembership> findByUserId(UUID userId);

	Optional<TeamMembership> findByTeamIdAndUserId(UUID teamId, UUID userId);

	List<TeamMembership> findByUserIdAndRole(UUID userId, String role);
}
