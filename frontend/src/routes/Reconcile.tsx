/**
 * Reconcile view — post-lock outcome recording and scope-change review.
 * Route: /weekly/reconcile/:planId?
 */
import { useParams } from "react-router-dom";

export default function Reconcile() {
  const { planId } = useParams<{ planId?: string }>();

  return (
    <div className="route-page" data-testid="page-reconcile">
      <h2>Reconcile</h2>
      {planId !== undefined && (
        <p>Plan ID: {planId}</p>
      )}
      <p>Lock flow and reconcile view — coming soon.</p>
    </div>
  );
}
