package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.CapacityOverride;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CapacityOverrideRepository extends JpaRepository<CapacityOverride, UUID> {
}
