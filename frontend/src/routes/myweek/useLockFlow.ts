/**
 * Lock button, pre-lock validation, lock confirm dialog, and lock execution.
 */
import { useState, useCallback, useMemo } from "react";
import { getEffectivePreLockErrors } from "../../components/lock/lockValidation.js";
import type { CommitResponse, LockValidationError } from "../../api/planTypes.js";
import type { PlanApi } from "../../api/planApi.js";

export function useLockFlow(
  plan: { id: string } | undefined,
  commits: CommitResponse[],
  planApi: PlanApi,
  refetchPlan: () => void,
  setActionError: (error: string | null) => void,
) {
  const [lockLoading, setLockLoading] = useState(false);
  const [showPreLockValidation, setShowPreLockValidation] = useState(false);
  const [lockValidationErrors, setLockValidationErrors] = useState<LockValidationError[]>([]);
  const [showLockConfirm, setShowLockConfirm] = useState(false);

  const effectivePreLockErrors = useMemo(
    () => getEffectivePreLockErrors(commits, lockValidationErrors),
    [commits, lockValidationErrors],
  );

  const handleLockButtonClick = useCallback(() => {
    setShowPreLockValidation(true);
    setLockValidationErrors([]);
    setShowLockConfirm(false);
  }, []);

  const handlePreLockContinue = useCallback(() => {
    setShowLockConfirm(true);
  }, []);

  const handleLock = useCallback(async () => {
    if (!plan) return;
    setLockLoading(true);
    setActionError(null);
    try {
      const response = await planApi.lockPlan(plan.id);
      if (!response.success) {
        setLockValidationErrors(response.errors ?? []);
        setShowLockConfirm(false);
        return;
      }
      setShowPreLockValidation(false);
      setShowLockConfirm(false);
      setLockValidationErrors([]);
      refetchPlan();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Lock failed");
      setShowLockConfirm(false);
    } finally {
      setLockLoading(false);
    }
  }, [plan, planApi, refetchPlan, setActionError]);

  return {
    lockLoading,
    showPreLockValidation,
    setShowPreLockValidation,
    lockValidationErrors,
    showLockConfirm,
    effectivePreLockErrors,
    handleLockButtonClick,
    handlePreLockContinue,
    handleLock,
  };
}
