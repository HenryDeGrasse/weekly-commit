package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.ReconcileSnapshotHeader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconcileSnapshotHeaderRepository extends JpaRepository<ReconcileSnapshotHeader, UUID> {

	Optional<ReconcileSnapshotHeader> findByPlanId(UUID planId);
}
