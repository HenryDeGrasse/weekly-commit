package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.AiFeedback;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiFeedbackRepository extends JpaRepository<AiFeedback, UUID> {
}
