/**
 * CarryForwardLineageView — visualization of a carry-forward provenance chain.
 */
import { cn } from "../../lib/utils.js";
import type { CarryForwardLineageResponse, CarryForwardNode } from "../../api/ticketTypes.js";
import type { CommitOutcome } from "../../api/planTypes.js";

export interface CarryForwardLineageViewProps {
  readonly lineage: CarryForwardLineageResponse;
  readonly loading?: boolean;
}

const OUTCOME_STYLES: Record<CommitOutcome, { cls: string; label: string }> = {
  ACHIEVED:           { cls: "bg-emerald-100 text-emerald-800",  label: "✓ Achieved" },
  PARTIALLY_ACHIEVED: { cls: "bg-amber-100 text-amber-800",      label: "◑ Partial" },
  NOT_ACHIEVED:       { cls: "bg-red-100 text-red-800",          label: "✗ Not achieved" },
  CANCELED:           { cls: "bg-slate-100 text-slate-500",       label: "— Canceled" },
};

function ChainNode({ node, isCurrent }: { readonly node: CarryForwardNode; readonly isCurrent: boolean }) {
  const outcomeMeta = node.outcome ? OUTCOME_STYLES[node.outcome] : null;

  return (
    <div
      data-testid={`cf-node-${node.commitId}`}
      className={cn(
        "rounded-default border p-3 min-w-[130px] max-w-[180px] flex flex-col gap-1 shrink-0",
        isCurrent
          ? "border-primary bg-primary text-white"
          : "border-border bg-surface text-foreground",
      )}
    >
      <span data-testid={`cf-node-week-${node.commitId}`} className={cn("text-xs font-bold", isCurrent && "opacity-80")}>
        {node.weekStartDate}
      </span>
      <span data-testid={`cf-node-title-${node.commitId}`} className="text-xs font-semibold overflow-hidden text-ellipsis whitespace-nowrap" title={node.title}>
        {node.title}
      </span>
      {node.streak > 0 && (
        <span data-testid={`cf-node-streak-${node.commitId}`} className={cn("text-[0.65rem] rounded-full px-1.5 py-px font-semibold self-start", isCurrent ? "bg-white/25 text-white" : "bg-amber-100 text-amber-800")}>
          {node.streak}× CF
        </span>
      )}
      {outcomeMeta ? (
        <span data-testid={`cf-node-outcome-${node.commitId}`} className={cn("text-[0.65rem] rounded-full px-1.5 py-px font-semibold self-start", isCurrent ? "bg-white/20 text-white" : outcomeMeta.cls)}>
          {outcomeMeta.label}
        </span>
      ) : (
        <span data-testid={`cf-node-pending-${node.commitId}`} className={cn("text-[0.65rem]", isCurrent ? "text-white/70" : "text-muted")}>
          {isCurrent ? "Current" : "Pending"}
        </span>
      )}
    </div>
  );
}

export function CarryForwardLineageView({ lineage, loading = false }: CarryForwardLineageViewProps) {
  if (loading) {
    return (
      <div data-testid="cf-lineage-loading" role="status" aria-label="Loading lineage" className="text-sm text-muted">
        Loading lineage…
      </div>
    );
  }

  if (lineage.chain.length === 0) {
    return (
      <div data-testid="cf-lineage-empty" className="py-4 text-center text-sm text-muted">
        No carry-forward history.
      </div>
    );
  }

  return (
    <section data-testid="cf-lineage-view" aria-label="Carry-forward provenance chain">
      <h4 className="m-0 mb-3 text-xs font-bold uppercase tracking-widest text-muted">
        Carry-forward Lineage
      </h4>
      <div data-testid="cf-lineage-chain" className="flex items-center overflow-x-auto pb-2">
        {lineage.chain.map((node, idx) => (
          <div key={node.commitId} className="flex items-center">
            {idx > 0 && (
              <span aria-hidden="true" className="text-muted px-1 shrink-0">→</span>
            )}
            <ChainNode node={node} isCurrent={node.commitId === lineage.currentCommitId} />
          </div>
        ))}
      </div>
      <p data-testid="cf-lineage-length" className="mt-2 text-xs text-muted">
        {lineage.chain.length} {lineage.chain.length === 1 ? "entry" : "entries"} in chain
      </p>
    </section>
  );
}
