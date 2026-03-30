/**
 * All AI collapsible sections: risk banners, recommendations, insights,
 * calibration, lint, and what-if planner.
 */
import { Component, type ErrorInfo, type ReactNode } from "react";
import { Badge } from "../../components/ui/Badge.js";
import { Button } from "../../components/ui/Button.js";
import { CollapsibleSection } from "../../components/shared/CollapsibleSection.js";
import { InsightPanel } from "../../components/ai/InsightPanel.js";
import { CalibrationCard } from "../../components/ai/CalibrationCard.js";
import { AiLintPanel } from "../../components/ai/AiLintPanel.js";
import { ProactiveRiskBanner } from "../../components/ai/ProactiveRiskBanner.js";
import { PlanRecommendationCard } from "../../components/ai/PlanRecommendationCard.js";
import { WhatIfPanel } from "../../components/ai/WhatIfPanel.js";
import type { CommitResponse } from "../../api/planTypes.js";
import type { PlanRecommendation } from "../../api/recommendationApi.js";
import type { CalibrationProfile } from "../../api/calibrationApi.js";

// ── Error Boundary (re-exported for use in MyWeek) ─────────────────────────

export class AiErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };
  static getDerivedStateFromError() {
    return { hasError: true };
  }
  componentDidCatch(_: Error, __: ErrorInfo) {
    /* silently suppress */
  }
  render() {
    return this.state.hasError ? null : this.props.children;
  }
}

// ── Types ───────────────────────────────────────────────────────────────────

interface AiSectionsProps {
  readonly aiAssistanceEnabled: boolean;
  readonly plan: { id: string; state: string } | undefined;
  readonly commits: CommitResponse[];
  readonly isDraft: boolean;
  readonly isLocked: boolean;
  readonly currentUserId: string;

  // AI feature state
  readonly aiMode: string;
  readonly sectionsOverride: boolean | undefined;
  readonly riskSignalCount: number | null;
  readonly setRiskSignalCount: (count: number | null) => void;
  readonly canShowRecommendations: boolean;
  readonly recommendationsLoading: boolean;
  readonly visibleRecommendations: readonly PlanRecommendation[];
  readonly recommendationRefreshKey: number;
  readonly recommendationRefreshing: boolean;
  readonly dismissRecommendation: (id: string) => void;
  readonly handleRefreshRecommendations: () => Promise<void>;
  readonly shouldAutoCollapseInsights: boolean;
  readonly recordInsightsDismiss: () => void;
  readonly showCalibration: boolean;
  readonly calibrationData: CalibrationProfile | null | undefined;
  readonly calibrationLoading: boolean;
  readonly shouldAutoCollapseLint: boolean;
  readonly recordLintDismiss: () => void;
  readonly lintRefreshKey: number;
  readonly lintHintCount: number | null;
  readonly setLintHintCount: (count: number | null) => void;
}

// ── Component ───────────────────────────────────────────────────────────────

export function AiSections({
  aiAssistanceEnabled,
  plan,
  commits,
  isDraft,
  isLocked,
  currentUserId,
  aiMode,
  sectionsOverride,
  riskSignalCount,
  setRiskSignalCount,
  canShowRecommendations,
  recommendationsLoading,
  visibleRecommendations,
  recommendationRefreshKey,
  recommendationRefreshing,
  dismissRecommendation,
  handleRefreshRecommendations,
  shouldAutoCollapseInsights,
  recordInsightsDismiss,
  showCalibration,
  calibrationData,
  calibrationLoading,
  shouldAutoCollapseLint,
  recordLintDismiss,
  lintRefreshKey,
  lintHintCount,
  setLintHintCount,
}: AiSectionsProps) {
  return (
    <>
      {/* Proactive risk banners */}
      {aiAssistanceEnabled && isLocked && plan && riskSignalCount !== 0 && (
        <CollapsibleSection
          id="risk-banners"
          title="Risk Signals"
          defaultExpanded={aiMode !== "on-demand"}
          overrideExpanded={sectionsOverride}
          badge={
            riskSignalCount != null ? (
              <span className="text-[0.65rem] font-bold px-1.5 py-0.5 rounded-sm bg-warning-bg text-warning border border-warning-border">
                ⚠ {riskSignalCount}
              </span>
            ) : undefined
          }
        >
          <AiErrorBoundary>
            <ProactiveRiskBanner planId={plan.id} onSignalCount={setRiskSignalCount} />
          </AiErrorBoundary>
        </CollapsibleSection>
      )}

      {/* Plan recommendations */}
      {canShowRecommendations && (recommendationsLoading || visibleRecommendations.length > 0) && (
        <CollapsibleSection
          id="plan-recommendations"
          title="Plan Recommendations"
          defaultExpanded={aiMode !== "on-demand"}
          overrideExpanded={sectionsOverride}
          badge={
            !recommendationsLoading && visibleRecommendations.length > 0 ? (
              <span className="text-[0.65rem] font-bold px-1.5 py-0.5 rounded-sm bg-info-bg text-info border border-info-border">
                {visibleRecommendations.length}
              </span>
            ) : undefined
          }
        >
          <AiErrorBoundary>
            <div className="flex flex-col gap-2" data-testid="plan-recommendations-section">
              <div className="flex justify-end">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => void handleRefreshRecommendations()}
                  disabled={recommendationRefreshing}
                  data-testid="refresh-recommendations-btn"
                >
                  {recommendationRefreshing ? "Refreshing…" : "Refresh recommendations"}
                </Button>
              </div>
              {recommendationsLoading && (
                <div
                  data-testid="recommendations-loading"
                  role="status"
                  aria-label="Loading recommendations"
                  className="flex flex-col gap-2"
                >
                  {[0, 1].map((i) => (
                    <div key={i} className="h-24 rounded-default border border-border bg-muted-bg animate-pulse" />
                  ))}
                </div>
              )}
              {!recommendationsLoading &&
                visibleRecommendations.map((rec) => (
                  <PlanRecommendationCard
                    key={`${rec.suggestionId}-${recommendationRefreshKey}`}
                    recommendation={rec}
                    onDismiss={dismissRecommendation}
                  />
                ))}
            </div>
          </AiErrorBoundary>
        </CollapsibleSection>
      )}

      {/* Personal AI insights */}
      {aiAssistanceEnabled && plan && (
        <CollapsibleSection
          id="ai-insights"
          title="AI Insights"
          defaultExpanded={aiMode === "on-demand" ? false : shouldAutoCollapseInsights ? false : false}
          overrideExpanded={sectionsOverride}
          onToggle={(expanded) => {
            if (!expanded) recordInsightsDismiss();
          }}
        >
          <InsightPanel mode="personal" planId={plan.id} />
        </CollapsibleSection>
      )}

      {/* Calibration profile */}
      {aiAssistanceEnabled && showCalibration && (
        <AiErrorBoundary>
          <CalibrationCard
            profile={
              calibrationData ?? {
                available: false,
                overallAchievementRate: 0,
                chessPieceAchievementRates: {},
                carryForwardProbability: 0,
                weeksOfData: 0,
                avgEstimateByPiece: {},
                confidenceTier: "INSUFFICIENT",
              }
            }
            loading={calibrationLoading}
            data-testid="my-week-calibration-card"
          />
        </AiErrorBoundary>
      )}

      {/* AI Quality Check */}
      {aiAssistanceEnabled && isDraft && plan && commits.length > 0 && (
        <CollapsibleSection
          id="ai-lint"
          title="AI Quality Check"
          defaultExpanded={aiMode === "on-demand" ? false : shouldAutoCollapseLint ? false : false}
          badge={
            lintHintCount != null ? (
              <Badge variant="draft">
                {lintHintCount} {lintHintCount === 1 ? "hint" : "hints"}
              </Badge>
            ) : undefined
          }
          testId="inline-ai-lint-panel"
          overrideExpanded={sectionsOverride}
          onToggle={(expanded) => {
            if (!expanded) recordLintDismiss();
          }}
        >
          <AiErrorBoundary>
            <AiLintPanel
              planId={plan.id}
              userId={currentUserId}
              autoRun={aiMode !== "on-demand"}
              refreshKey={lintRefreshKey}
              onHintCountChange={setLintHintCount}
            />
          </AiErrorBoundary>
        </CollapsibleSection>
      )}

      {/* What-If Planner */}
      {aiAssistanceEnabled && plan && (isDraft || isLocked) && (
        <CollapsibleSection id="what-if" title="What-If Planner" defaultExpanded={false} overrideExpanded={sectionsOverride}>
          <AiErrorBoundary>
            <WhatIfPanel planId={plan.id} currentCommits={commits} />
          </AiErrorBoundary>
        </CollapsibleSection>
      )}
    </>
  );
}
