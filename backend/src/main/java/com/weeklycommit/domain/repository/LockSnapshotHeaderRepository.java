package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.LockSnapshotHeader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LockSnapshotHeaderRepository extends JpaRepository<LockSnapshotHeader, UUID> {

	Optional<LockSnapshotHeader> findByPlanId(UUID planId);
}
