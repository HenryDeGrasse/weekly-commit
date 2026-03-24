package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.LockSnapshotCommit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LockSnapshotCommitRepository extends JpaRepository<LockSnapshotCommit, UUID> {
}
