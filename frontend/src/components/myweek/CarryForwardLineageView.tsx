/**
 * CarryForwardLineageView — visualization of a carry-forward provenance chain.
 *
 * Each node in the chain shows: week, outcome, streak count.
 * The chain flows left to right (oldest → newest).
 * Accessible via aria-label on the chain container.
 */
import type { CarryForwardLineageResponse, CarryForwardNode } from "../../api/ticketTypes.js";
import type { CommitOutcome } from "../../api/planTypes.js";

export interface CarryForwardLineageViewProps {
  readonly lineage: CarryForwardLineageResponse;
  readonly loading?: boolean;
}

const OUTCOME_STYLES: Record<
  CommitOutcome,
  { bg: string; color: string; label: string }
> = {
  ACHIEVED: { bg: "#d1fae5", color: "#065f46", label: "✓ Achieved" },
  PARTIALLY_ACHIEVED: { bg: "#fef3c7", color: "#92400e", label: "◑ Partial" },
  NOT_ACHIEVED: { bg: "#fee2e2", color: "#991b1b", label: "✗ Not achieved" },
  CANCELED: { bg: "#f1f5f9", color: "#94a3b8", label: "— Canceled" },
};

function ChainNode({
  node,
  isCurrent,
}: {
  readonly node: CarryForwardNode;
  readonly isCurrent: boolean;
}) {
  const outcomeMeta = node.outcome ? OUTCOME_STYLES[node.outcome] : null;

  return (
    <div
      data-testid={`cf-node-${node.commitId}`}
      style={{
        background: isCurrent
          ? "var(--color-primary)"
          : "var(--color-surface)",
        color: isCurrent ? "#fff" : "var(--color-text)",
        border: isCurrent
          ? "2px solid var(--color-primary)"
          : "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        padding: "0.625rem 0.875rem",
        minWidth: "130px",
        maxWidth: "180px",
        display: "flex",
        flexDirection: "column",
        gap: "0.25rem",
        flexShrink: 0,
      }}
    >
      <span
        data-testid={`cf-node-week-${node.commitId}`}
        style={{ fontSize: "0.75rem", fontWeight: 700, opacity: isCurrent ? 0.85 : 1 }}
      >
        {node.weekStartDate}
      </span>
      <span
        data-testid={`cf-node-title-${node.commitId}`}
        style={{
          fontSize: "0.8rem",
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
          fontWeight: 600,
        }}
        title={node.title}
      >
        {node.title}
      </span>
      {node.streak > 0 && (
        <span
          data-testid={`cf-node-streak-${node.commitId}`}
          style={{
            fontSize: "0.7rem",
            background: isCurrent ? "rgba(255,255,255,0.25)" : "#fef3c7",
            color: isCurrent ? "#fff" : "#92400e",
            padding: "1px 6px",
            borderRadius: "999px",
            alignSelf: "flex-start",
            fontWeight: 600,
          }}
        >
          {node.streak}× CF
        </span>
      )}
      {outcomeMeta ? (
        <span
          data-testid={`cf-node-outcome-${node.commitId}`}
          style={{
            fontSize: "0.7rem",
            background: isCurrent ? "rgba(255,255,255,0.2)" : outcomeMeta.bg,
            color: isCurrent ? "#fff" : outcomeMeta.color,
            padding: "1px 6px",
            borderRadius: "999px",
            alignSelf: "flex-start",
            fontWeight: 600,
          }}
        >
          {outcomeMeta.label}
        </span>
      ) : (
        <span
          data-testid={`cf-node-pending-${node.commitId}`}
          style={{
            fontSize: "0.7rem",
            color: isCurrent ? "rgba(255,255,255,0.7)" : "var(--color-text-muted)",
          }}
        >
          {isCurrent ? "Current" : "Pending"}
        </span>
      )}
    </div>
  );
}

const ArrowSep = () => (
  <div
    aria-hidden="true"
    style={{
      display: "flex",
      alignItems: "center",
      flexShrink: 0,
      color: "var(--color-text-muted)",
      fontSize: "1rem",
      padding: "0 0.25rem",
    }}
  >
    →
  </div>
);

export function CarryForwardLineageView({
  lineage,
  loading = false,
}: CarryForwardLineageViewProps) {
  if (loading) {
    return (
      <div
        data-testid="cf-lineage-loading"
        role="status"
        aria-label="Loading lineage"
        style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
      >
        Loading lineage…
      </div>
    );
  }

  if (lineage.chain.length === 0) {
    return (
      <div
        data-testid="cf-lineage-empty"
        style={{
          padding: "1rem",
          textAlign: "center",
          color: "var(--color-text-muted)",
          fontSize: "0.875rem",
        }}
      >
        No carry-forward history.
      </div>
    );
  }

  return (
    <section
      data-testid="cf-lineage-view"
      aria-label="Carry-forward provenance chain"
    >
      <h4
        style={{ margin: "0 0 0.75rem", fontSize: "0.875rem", fontWeight: 700, color: "var(--color-text-muted)", textTransform: "uppercase", letterSpacing: "0.04em" }}
      >
        Carry-forward Lineage
      </h4>
      <div
        data-testid="cf-lineage-chain"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "0",
          overflowX: "auto",
          paddingBottom: "0.5rem",
        }}
      >
        {lineage.chain.map((node, idx) => (
          <div
            key={node.commitId}
            style={{ display: "flex", alignItems: "center", gap: "0" }}
          >
            {idx > 0 && <ArrowSep />}
            <ChainNode
              node={node}
              isCurrent={node.commitId === lineage.currentCommitId}
            />
          </div>
        ))}
      </div>
      <p
        data-testid="cf-lineage-length"
        style={{
          margin: "0.5rem 0 0",
          fontSize: "0.75rem",
          color: "var(--color-text-muted)",
        }}
      >
        {lineage.chain.length} {lineage.chain.length === 1 ? "entry" : "entries"} in chain
      </p>
    </section>
  );
}
