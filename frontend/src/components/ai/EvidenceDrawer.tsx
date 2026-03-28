/**
 * EvidenceDrawer — shows exactly what the AI used to produce its answer.
 *
 * Renders four panes:
 *   1. Facts — SQL-sourced exact numbers (points, compliance, chess distribution)
 *   2. Lineage — carry-forward chain from origin to current
 *   3. Evidence — semantic matches from vector retrieval
 *   4. Risk — pre-computed risk features and active signals
 *
 * Every datum is traceable to a specific source — no black-box AI outputs.
 */
import { useState } from "react";
import { ChevronDown, ChevronRight, Database, GitBranch, Search, AlertTriangle } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { Card, CardContent, CardHeader, CardTitle } from "../ui/Card.js";
import { cn } from "../../lib/utils.js";

// ── Types mirroring backend StructuredEvidence ──────────────────────────────

interface SqlFacts {
  userDisplayName: string;
  teamName: string;
  weekStart: string;
  planState: string;
  capacityBudget: number;
  totalPlannedPoints: number;
  totalAchievedPoints: number;
  commitCount: number;
  carryForwardCount: number;
  scopeChangeCount: number;
  lockCompliance: boolean;
  reconcileCompliance: boolean;
  chessDistribution: Record<string, number>;
}

interface LineageNode {
  commitId: string;
  title: string;
  weekStart: string;
  outcome: string | null;
  chessPiece: string | null;
  estimatePoints: number | null;
  carryForwardReason: string | null;
}

interface LineageChain {
  currentCommitId: string;
  currentTitle: string;
  streakLength: number;
  nodes: LineageNode[];
}

interface SemanticMatch {
  entityType: string;
  entityId: string;
  score: number;
  weekStartDate: string;
  text: string;
}

interface RiskFeatures {
  completionRatio: number;
  avgCompletionRatio4w: number;
  carryForwardStreakMax: number;
  scopeChangeCount: number;
  kingCount: number;
  queenCount: number;
  activeRiskSignalTypes: string[];
}

export interface StructuredEvidence {
  sqlFacts: SqlFacts | null;
  lineage: LineageChain | null;
  semanticMatches: SemanticMatch[];
  riskFeatures: RiskFeatures | null;
}

interface EvidenceDrawerProps {
  evidence: StructuredEvidence | null;
  className?: string | undefined;
}

// ── Collapsible section ─────────────────────────────────────────────────────

function Section({
  title,
  icon,
  children,
  defaultOpen = false,
  badge,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  defaultOpen?: boolean;
  badge?: string;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-border last:border-b-0">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 w-full px-3 py-2 text-xs font-semibold text-muted hover:bg-muted-bg/50 transition-colors"
      >
        {open ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
        {icon}
        <span className="uppercase tracking-wider">{title}</span>
        {badge && (
          <Badge variant="default" className="ml-auto text-[0.55rem]">
            {badge}
          </Badge>
        )}
      </button>
      {open && <div className="px-3 pb-3">{children}</div>}
    </div>
  );
}

// ── Main component ──────────────────────────────────────────────────────────

export function EvidenceDrawer({ evidence, className }: EvidenceDrawerProps) {
  if (!evidence) {
    return null;
  }

  const { sqlFacts, lineage, semanticMatches, riskFeatures } = evidence;
  const hasAny = sqlFacts || lineage || semanticMatches.length > 0 || riskFeatures;

  if (!hasAny) {
    return null;
  }

  return (
    <Card
      className={cn("w-full", className)}
      data-testid="evidence-drawer"
    >
      <CardHeader>
        <CardTitle className="text-xs font-semibold text-muted uppercase tracking-wider">
          Evidence Used by AI
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {/* Facts */}
        {sqlFacts && (
          <Section
            title="Facts"
            icon={<Database className="h-3 w-3" />}
            defaultOpen
            badge="SQL"
          >
            <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <Fact label="Owner" value={sqlFacts.userDisplayName} />
              <Fact label="Team" value={sqlFacts.teamName} />
              <Fact label="Week" value={sqlFacts.weekStart} />
              <Fact label="State" value={sqlFacts.planState} />
              <Fact
                label="Points"
                value={`${sqlFacts.totalAchievedPoints}/${sqlFacts.totalPlannedPoints} of ${sqlFacts.capacityBudget} budget`}
              />
              <Fact label="Commits" value={String(sqlFacts.commitCount)} />
              <Fact label="Carry-forwards" value={String(sqlFacts.carryForwardCount)} />
              <Fact label="Scope changes" value={String(sqlFacts.scopeChangeCount)} />
              <Fact
                label="Lock"
                value={sqlFacts.lockCompliance ? "✓ compliant" : "✗ non-compliant"}
              />
              <Fact
                label="Reconcile"
                value={sqlFacts.reconcileCompliance ? "✓ compliant" : "✗ non-compliant"}
              />
            </div>
            {sqlFacts.chessDistribution && Object.keys(sqlFacts.chessDistribution).length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {Object.entries(sqlFacts.chessDistribution).map(([piece, count]) => (
                  <Badge key={piece} variant="default" className="text-[0.55rem]">
                    {piece}: {count}
                  </Badge>
                ))}
              </div>
            )}
          </Section>
        )}

        {/* Lineage */}
        {lineage && lineage.nodes.length > 0 && (
          <Section
            title="Lineage"
            icon={<GitBranch className="h-3 w-3" />}
            badge={`${lineage.streakLength} week streak`}
          >
            <div className="flex flex-col gap-1">
              {lineage.nodes.map((node, i) => (
                <div
                  key={node.commitId}
                  className={cn(
                    "flex items-start gap-2 text-xs",
                    i === lineage.nodes.length - 1 ? "font-semibold" : "text-muted",
                  )}
                >
                  <span className="text-[0.6rem] text-muted shrink-0 mt-0.5 w-20">
                    {node.weekStart}
                  </span>
                  <span className="flex-1 min-w-0 truncate">{node.title}</span>
                  {node.outcome && (
                    <Badge
                      variant="default"
                      className={cn(
                        "text-[0.5rem] shrink-0",
                        node.outcome === "ACHIEVED" && "bg-foreground/10",
                        node.outcome === "NOT_ACHIEVED" && "bg-neutral-200",
                      )}
                    >
                      {node.outcome}
                    </Badge>
                  )}
                </div>
              ))}
            </div>
          </Section>
        )}

        {/* Semantic evidence */}
        {semanticMatches.length > 0 && (
          <Section
            title="Evidence"
            icon={<Search className="h-3 w-3" />}
            badge={`${semanticMatches.length} matches`}
          >
            <div className="flex flex-col gap-1.5">
              {semanticMatches.slice(0, 5).map((match) => (
                <div
                  key={match.entityId}
                  className="text-xs border border-border rounded-sm px-2 py-1.5"
                >
                  <div className="flex items-center gap-1.5 mb-0.5">
                    <Badge variant="default" className="text-[0.5rem]">
                      {match.entityType}
                    </Badge>
                    <span className="text-muted text-[0.6rem]">{match.weekStartDate}</span>
                    <span className="text-muted text-[0.6rem] ml-auto">
                      {(match.score * 100).toFixed(0)}%
                    </span>
                  </div>
                  {match.text && (
                    <p className="m-0 text-[0.65rem] text-muted line-clamp-2">{match.text}</p>
                  )}
                </div>
              ))}
            </div>
          </Section>
        )}

        {/* Risk features */}
        {riskFeatures && (
          <Section
            title="Risk"
            icon={<AlertTriangle className="h-3 w-3" />}
            badge={riskFeatures.activeRiskSignalTypes.length > 0
              ? `${riskFeatures.activeRiskSignalTypes.length} active`
              : "none"
            }
          >
            <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <Fact
                label="Completion"
                value={`${(riskFeatures.completionRatio * 100).toFixed(0)}% this week`}
              />
              <Fact
                label="4-week avg"
                value={`${(riskFeatures.avgCompletionRatio4w * 100).toFixed(0)}%`}
              />
              <Fact label="Max CF streak" value={String(riskFeatures.carryForwardStreakMax)} />
              <Fact label="Scope changes" value={String(riskFeatures.scopeChangeCount)} />
              <Fact label="Kings" value={String(riskFeatures.kingCount)} />
              <Fact label="Queens" value={String(riskFeatures.queenCount)} />
            </div>
            {riskFeatures.activeRiskSignalTypes.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {riskFeatures.activeRiskSignalTypes.map((type) => (
                  <Badge key={type} variant="danger" className="text-[0.55rem]">
                    {type}
                  </Badge>
                ))}
              </div>
            )}
          </Section>
        )}
      </CardContent>
    </Card>
  );
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-muted">{label}</span>
      <span className="font-medium text-foreground">{value}</span>
    </div>
  );
}
