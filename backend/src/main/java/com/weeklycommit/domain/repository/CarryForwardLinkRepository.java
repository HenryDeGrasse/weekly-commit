package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.CarryForwardLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarryForwardLinkRepository extends JpaRepository<CarryForwardLink, UUID> {

	List<CarryForwardLink> findBySourceCommitId(UUID sourceCommitId);

	Optional<CarryForwardLink> findByTargetCommitId(UUID targetCommitId);
}
