package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByOrganizationIdAndEmail(UUID organizationId, String email);

	List<UserAccount> findByHomeTeamId(UUID homeTeamId);

	List<UserAccount> findByOrganizationIdAndActive(UUID organizationId, boolean active);
}
