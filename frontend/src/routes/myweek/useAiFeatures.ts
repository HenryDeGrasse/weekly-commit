/**
 * AI-related state: lint, risk signals, calibration, recommendations,
 * AI composer, dismiss memory, AI mode, and sections override.
 */
import { useState, useCallback, useEffect, useRef } from "react";
import { useAiStatus } from "../../api/aiHooks.js";
import { useCalibration } from "../../api/calibrationHooks.js";
import { usePlanRecommendations, useRecommendationApi } from "../../api/recommendationHooks.js";
import { useDismissMemory } from "../../lib/useDismissMemory.js";
import { useDismissedIds } from "../../lib/useDismissedIds.js";
import { useAiMode } from "../../lib/useAiMode.js";

export function useAiFeatures(
  aiAssistanceEnabled: boolean,
  currentUserId: string,
  plan: { id: string; state: string } | undefined,
  isDraft: boolean,
  isLocked: boolean,
) {
  const { data: aiStatus } = useAiStatus();
  const { aiMode } = useAiMode();

  // Dismiss memory for AI sections
  const { shouldAutoCollapse: shouldAutoCollapseLint, recordDismiss: recordLintDismiss } =
    useDismissMemory("ai-lint");
  const { shouldAutoCollapse: shouldAutoCollapseInsights, recordDismiss: recordInsightsDismiss } =
    useDismissMemory("ai-insights");

  // AI composer
  const aiComposerEnabled = aiAssistanceEnabled && (aiStatus?.available ?? false);
  const [showAiComposer, setShowAiComposer] = useState(false);

  // Lint state
  const [lintRefreshKey, setLintRefreshKey] = useState(0);
  const [lintHintCount, setLintHintCount] = useState<number | null>(null);

  // Risk signals
  const [riskSignalCount, setRiskSignalCount] = useState<number | null>(null);

  // Calibration
  const { data: calibrationData, loading: calibrationLoading } = useCalibration(
    aiAssistanceEnabled ? currentUserId : null,
  );
  const showCalibration =
    calibrationLoading || (calibrationData != null && calibrationData.weeksOfData >= 8);

  // Recommendations
  const canShowRecommendations = aiAssistanceEnabled && plan != null && (isDraft || isLocked);
  const {
    data: recommendationsData,
    loading: recommendationsLoading,
    refetch: refetchRecommendations,
  } = usePlanRecommendations(canShowRecommendations ? plan.id : null);
  const recommendationApi = useRecommendationApi();
  const { dismissedIds: dismissedRecommendationIds, dismiss: dismissRecommendation } =
    useDismissedIds("plan-recommendations");
  const visibleRecommendations = (recommendationsData ?? []).filter(
    (r) => !dismissedRecommendationIds.has(r.suggestionId),
  );
  const [recommendationRefreshKey, setRecommendationRefreshKey] = useState(0);
  const [recommendationRefreshing, setRecommendationRefreshing] = useState(false);

  const handleRefreshRecommendations = useCallback(async () => {
    if (!plan) return;
    setRecommendationRefreshing(true);
    try {
      await recommendationApi.refreshRecommendations(plan.id);
      refetchRecommendations();
    } catch {
      // Silently degrade — non-critical
    } finally {
      setRecommendationRefreshing(false);
      setRecommendationRefreshKey((k) => k + 1);
    }
  }, [plan, recommendationApi, refetchRecommendations]);

  // Expand / Collapse all sections
  const [sectionsOverride, setSectionsOverride] = useState<boolean | undefined>(undefined);
  const overrideAppliedRef = useRef(false);

  useEffect(() => {
    if (sectionsOverride !== undefined) {
      if (!overrideAppliedRef.current) {
        overrideAppliedRef.current = true;
      } else {
        setSectionsOverride(undefined);
        overrideAppliedRef.current = false;
      }
    }
  }, [sectionsOverride]);

  const handleExpandAll = useCallback(() => {
    overrideAppliedRef.current = false;
    setSectionsOverride(true);
  }, []);

  const handleCollapseAll = useCallback(() => {
    overrideAppliedRef.current = false;
    setSectionsOverride(false);
  }, []);

  return {
    aiMode,
    aiComposerEnabled,
    showAiComposer,
    setShowAiComposer,
    lintRefreshKey,
    setLintRefreshKey,
    lintHintCount,
    setLintHintCount,
    riskSignalCount,
    setRiskSignalCount,
    calibrationData,
    calibrationLoading,
    showCalibration,
    canShowRecommendations,
    recommendationsLoading,
    visibleRecommendations,
    recommendationRefreshKey,
    recommendationRefreshing,
    dismissRecommendation,
    handleRefreshRecommendations,
    shouldAutoCollapseLint,
    recordLintDismiss,
    shouldAutoCollapseInsights,
    recordInsightsDismiss,
    sectionsOverride,
    handleExpandAll,
    handleCollapseAll,
  };
}
