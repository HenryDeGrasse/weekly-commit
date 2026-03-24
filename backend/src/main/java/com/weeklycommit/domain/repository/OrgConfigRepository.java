package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.OrgConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrgConfigRepository extends JpaRepository<OrgConfig, UUID> {

	Optional<OrgConfig> findByOrgId(UUID orgId);
}
