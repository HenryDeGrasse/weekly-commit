package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RcdoNodeRepository extends JpaRepository<RcdoNode, UUID> {

	List<RcdoNode> findByStatus(RcdoNodeStatus status);

	List<RcdoNode> findByParentId(UUID parentId);

	List<RcdoNode> findByParentIdAndStatus(UUID parentId, RcdoNodeStatus status);

	List<RcdoNode> findByNodeTypeAndStatus(RcdoNodeType nodeType, RcdoNodeStatus status);

	List<RcdoNode> findByOwnerTeamId(UUID ownerTeamId);
}
