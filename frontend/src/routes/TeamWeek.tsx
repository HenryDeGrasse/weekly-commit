/**
 * Team Week dashboard — manager view of team's weekly commitments.
 * Route: /weekly/team/:teamId?
 */
import { useParams } from "react-router-dom";

export default function TeamWeek() {
  const { teamId } = useParams<{ teamId?: string }>();

  return (
    <div className="route-page" data-testid="page-team-week">
      <h2>Team Week</h2>
      {teamId !== undefined && (
        <p>Team ID: {teamId}</p>
      )}
      <p>Team dashboard and manager review — coming soon.</p>
    </div>
  );
}
