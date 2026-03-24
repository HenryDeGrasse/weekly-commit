package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.TeamMembership;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {
}
