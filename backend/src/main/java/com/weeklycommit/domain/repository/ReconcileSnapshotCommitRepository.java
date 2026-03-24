package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.ReconcileSnapshotCommit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconcileSnapshotCommitRepository extends JpaRepository<ReconcileSnapshotCommit, UUID> {
}
