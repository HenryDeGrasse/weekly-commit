package com.weeklycommit.ai.evidence;

import com.weeklycommit.ai.evidence.StructuredEvidence.LineageChain;
import com.weeklycommit.ai.evidence.StructuredEvidence.LineageNode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lineage and provenance queries using recursive CTEs against PostgreSQL.
 *
 * <p>
 * Provides carry-forward chain traversal, scope-change timelines, and RCDO
 * ancestry paths — all from SQL, with no graph database dependency.
 */
@Service
@Transactional(readOnly = true)
public class LineageQueryService {

	private static final Logger log = LoggerFactory.getLogger(LineageQueryService.class);

	private final EntityManager em;

	public LineageQueryService(EntityManager em) {
		this.em = em;
	}

	/**
	 * Traces the full carry-forward lineage for a commit back to its origin.
	 * Returns a {@link LineageChain} with nodes ordered from oldest (origin) to
	 * newest (current).
	 *
	 * <p>
	 * Uses a recursive CTE on {@code carry_forward_link} joined with
	 * {@code weekly_commit} and {@code weekly_plan} for context.
	 *
	 * @param commitId
	 *            the commit to trace (may be any node in the chain)
	 * @return lineage chain, or {@code null} if the commit has no carry-forward
	 *         history
	 */
	public LineageChain traceLineage(UUID commitId) {
		if (commitId == null) {
			return null;
		}
		try {
			// Walk backward from the given commit to the origin
			@SuppressWarnings("unchecked")
			List<Object[]> backwardRows = em.createNativeQuery("""
					WITH RECURSIVE lineage_back AS (
					    SELECT cfl.source_commit_id, cfl.target_commit_id, cfl.reason, 1 AS depth
					    FROM carry_forward_link cfl
					    WHERE cfl.target_commit_id = :commitId
					    UNION ALL
					    SELECT cfl.source_commit_id, cfl.target_commit_id, cfl.reason, lb.depth + 1
					    FROM carry_forward_link cfl
					    JOIN lineage_back lb ON lb.source_commit_id = cfl.target_commit_id
					    WHERE lb.depth < 20
					)
					SELECT wc.id, wc.title, wp.week_start_date, wc.outcome, wc.chess_piece,
					       wc.estimate_points, lb.reason, lb.depth
					FROM lineage_back lb
					JOIN weekly_commit wc ON wc.id = lb.source_commit_id
					JOIN weekly_plan wp ON wp.id = wc.plan_id
					ORDER BY lb.depth DESC
					""").setParameter("commitId", commitId).getResultList();

			if (backwardRows.isEmpty()) {
				return null;
			}

			List<LineageNode> nodes = new ArrayList<>();

			// Add origin nodes (oldest first)
			for (Object[] row : backwardRows) {
				nodes.add(toLineageNode(row));
			}

			// Add the current commit itself as the last node
			@SuppressWarnings("unchecked")
			List<Object[]> currentRows = em.createNativeQuery("""
					SELECT wc.id, wc.title, wp.week_start_date, wc.outcome, wc.chess_piece,
					       wc.estimate_points
					FROM weekly_commit wc
					JOIN weekly_plan wp ON wp.id = wc.plan_id
					WHERE wc.id = :commitId
					""").setParameter("commitId", commitId).getResultList();

			String currentTitle = "";
			if (!currentRows.isEmpty()) {
				Object[] r = currentRows.get(0);
				currentTitle = r[1] != null ? r[1].toString() : "";
				nodes.add(new LineageNode(toUUID(r[0]), currentTitle,
						r[2] != null ? LocalDate.parse(r[2].toString()) : null, r[3] != null ? r[3].toString() : null,
						r[4] != null ? r[4].toString() : null, r[5] != null ? ((Number) r[5]).intValue() : null, null));
			}

			return new LineageChain(commitId, currentTitle, nodes.size() - 1, nodes);

		} catch (Exception e) {
			log.warn("LineageQueryService: traceLineage failed for commit {} — {}", commitId, e.getMessage());
			return null;
		}
	}

	/**
	 * Returns the full RCDO ancestry path for a node, from Rally Cry down to the
	 * node itself.
	 *
	 * @param rcdoNodeId
	 *            the leaf or intermediate RCDO node
	 * @return path like {@code ["Rally Cry: X", "DO: Y", "Outcome: Z"]}, or empty
	 */
	public List<String> rcdoAncestryPath(UUID rcdoNodeId) {
		if (rcdoNodeId == null) {
			return List.of();
		}
		try {
			@SuppressWarnings("unchecked")
			List<Object[]> rows = em.createNativeQuery("""
					WITH RECURSIVE ancestry AS (
					    SELECT id, title, node_type, parent_id, 0 AS depth
					    FROM rcdo_node WHERE id = :nodeId
					    UNION ALL
					    SELECT rn.id, rn.title, rn.node_type, rn.parent_id, a.depth + 1
					    FROM rcdo_node rn
					    JOIN ancestry a ON a.parent_id = rn.id
					    WHERE a.depth < 10
					)
					SELECT node_type, title FROM ancestry ORDER BY depth DESC
					""").setParameter("nodeId", rcdoNodeId).getResultList();

			return rows.stream().map(r -> r[0].toString().replace("_", " ") + ": " + r[1].toString()).toList();
		} catch (Exception e) {
			log.warn("LineageQueryService: rcdoAncestryPath failed for {} — {}", rcdoNodeId, e.getMessage());
			return List.of();
		}
	}

	/**
	 * Returns the scope change timeline for a plan, ordered chronologically.
	 */
	public List<ScopeChangeEntry> scopeChangeTimeline(UUID planId) {
		if (planId == null) {
			return List.of();
		}
		try {
			@SuppressWarnings("unchecked")
			List<Object[]> rows = em.createNativeQuery("""
					SELECT sce.category, sce.reason, sce.created_at,
					       wc.title AS commit_title, ua.display_name AS changed_by
					FROM scope_change_event sce
					LEFT JOIN weekly_commit wc ON wc.id = sce.commit_id
					LEFT JOIN user_account ua ON ua.id = sce.changed_by_user_id
					WHERE sce.plan_id = :planId
					ORDER BY sce.created_at ASC
					""").setParameter("planId", planId).getResultList();

			return rows.stream()
					.map(r -> new ScopeChangeEntry(r[0] != null ? r[0].toString() : "",
							r[1] != null ? r[1].toString() : "", r[2] != null ? r[2].toString() : "",
							r[3] != null ? r[3].toString() : null, r[4] != null ? r[4].toString() : null))
					.toList();
		} catch (Exception e) {
			log.warn("LineageQueryService: scopeChangeTimeline failed for plan {} — {}", planId, e.getMessage());
			return List.of();
		}
	}

	/** A single entry in a scope change timeline. */
	public record ScopeChangeEntry(String category, String reason, String timestamp, String commitTitle,
			String changedBy) {
	}

	// ── Private helpers ──────────────────────────────────────────────────

	private LineageNode toLineageNode(Object[] row) {
		return new LineageNode(toUUID(row[0]), row[1] != null ? row[1].toString() : "",
				row[2] != null ? LocalDate.parse(row[2].toString()) : null, row[3] != null ? row[3].toString() : null,
				row[4] != null ? row[4].toString() : null, row[5] != null ? ((Number) row[5]).intValue() : null,
				row[6] != null ? row[6].toString() : null);
	}

	private UUID toUUID(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof UUID) {
			return (UUID) obj;
		}
		return UUID.fromString(obj.toString());
	}
}
