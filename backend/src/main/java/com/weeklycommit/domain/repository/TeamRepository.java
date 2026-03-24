package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.Team;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

	List<Team> findByOrganizationId(UUID organizationId);

	List<Team> findByParentTeamId(UUID parentTeamId);
}
