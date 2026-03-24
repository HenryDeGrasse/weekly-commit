/**
 * TicketForm — modal form for creating or editing a native ticket.
 *
 * Fields:
 *   - title (required)
 *   - description (textarea)
 *   - status (select, default Backlog/TODO)
 *   - priority (required select)
 *   - assignee (text input for userId)
 *   - reporter (default = current user, read-only when pre-filled)
 *   - team (text input for teamId)
 *   - estimatePoints (optional Fibonacci select)
 *   - rcdoNodeId (optional text input)
 *   - targetWeekStartDate (optional date input)
 *
 * Supports "create-from-commit" mode via `initialValues` prop.
 */
import { useState, type FormEvent } from "react";
import type {
  CreateTicketPayload,
  TicketStatus,
  TicketPriority,
  TicketResponse,
} from "../../api/ticketTypes.js";
import type { EstimatePoints } from "../../api/planTypes.js";
import {
  TICKET_STATUS_LABELS,
  TICKET_PRIORITY_LABELS,
} from "../../api/ticketTypes.js";

const TICKET_STATUSES: TicketStatus[] = [
  "TODO",
  "IN_PROGRESS",
  "BLOCKED",
  "DONE",
  "CANCELED",
];
const TICKET_PRIORITIES: TicketPriority[] = [
  "CRITICAL",
  "HIGH",
  "MEDIUM",
  "LOW",
];
const ESTIMATE_POINTS: EstimatePoints[] = [1, 2, 3, 5, 8];

export interface TicketFormProps {
  readonly mode: "create" | "edit";
  /** Pre-populate fields (used for create-from-commit or editing). */
  readonly initialValues?: Partial<CreateTicketPayload>;
  readonly currentUserId: string;
  readonly currentTeamId?: string | undefined;
  /** Called with the full payload on submit. May throw to surface error in form. */
  readonly onSubmit: (payload: CreateTicketPayload) => Promise<void>;
  readonly onCancel: () => void;
  /** Optional existing ticket (edit mode). */
  readonly ticket?: TicketResponse;
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "0.5rem 0.625rem",
  border: "1px solid var(--color-border)",
  borderRadius: "var(--border-radius)",
  background: "var(--color-surface)",
  color: "var(--color-text)",
  fontFamily: "inherit",
  fontSize: "0.875rem",
  boxSizing: "border-box",
};

const labelStyle: React.CSSProperties = {
  display: "block",
  fontSize: "0.8rem",
  fontWeight: 600,
  color: "var(--color-text-muted)",
  marginBottom: "0.3rem",
};

const fieldStyle: React.CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: "0",
};

const errorStyle: React.CSSProperties = {
  fontSize: "0.75rem",
  color: "var(--color-danger)",
  marginTop: "0.25rem",
};

export function TicketForm({
  mode,
  initialValues,
  currentUserId,
  currentTeamId,
  onSubmit,
  onCancel,
  ticket,
}: TicketFormProps) {
  const [title, setTitle] = useState(
    ticket?.title ?? initialValues?.title ?? "",
  );
  const [description, setDescription] = useState(
    ticket?.description ?? initialValues?.description ?? "",
  );
  const [status, setStatus] = useState<TicketStatus>(
    ticket?.status ?? initialValues?.status ?? "TODO",
  );
  const [priority, setPriority] = useState<TicketPriority>(
    ticket?.priority ?? initialValues?.priority ?? "MEDIUM",
  );
  const [assignee, setAssignee] = useState(
    ticket?.assigneeUserId ?? initialValues?.assigneeUserId ?? "",
  );
  const [reporter, setReporter] = useState(
    ticket?.reporterUserId ?? initialValues?.reporterUserId ?? currentUserId,
  );
  const [teamId, setTeamId] = useState(
    ticket?.teamId ?? initialValues?.teamId ?? currentTeamId ?? "",
  );
  const [estimatePoints, setEstimatePoints] = useState<EstimatePoints | "">(
    ticket?.estimatePoints ?? initialValues?.estimatePoints ?? "",
  );
  const [rcdoNodeId, setRcdoNodeId] = useState(
    ticket?.rcdoNodeId ?? initialValues?.rcdoNodeId ?? "",
  );
  const [targetWeek, setTargetWeek] = useState(
    ticket?.targetWeekStartDate ?? initialValues?.targetWeekStartDate ?? "",
  );

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function validate(): boolean {
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = "Title is required.";
    if (!teamId.trim()) next.teamId = "Team is required.";
    if (!reporter.trim()) next.reporter = "Reporter is required.";
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    setSubmitError(null);
    try {
      const payload: CreateTicketPayload = {
        title: title.trim(),
        ...(description.trim() ? { description: description.trim() } : {}),
        status,
        priority,
        ...(assignee.trim() ? { assigneeUserId: assignee.trim() } : {}),
        reporterUserId: reporter.trim(),
        teamId: teamId.trim(),
        ...(rcdoNodeId.trim() ? { rcdoNodeId: rcdoNodeId.trim() } : {}),
        ...(estimatePoints !== ""
          ? { estimatePoints: estimatePoints as EstimatePoints }
          : {}),
        ...(targetWeek ? { targetWeekStartDate: targetWeek } : {}),
      };
      await onSubmit(payload);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Failed to save ticket.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={mode === "create" ? "Create ticket" : "Edit ticket"}
      data-testid="ticket-form-dialog"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1100,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.35)",
        overflow: "auto",
        padding: "1rem",
      }}
    >
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "1.5rem",
          width: "min(520px, 96vw)",
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
          maxHeight: "90vh",
          overflowY: "auto",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginBottom: "1.25rem",
          }}
        >
          <h3 style={{ margin: 0, fontSize: "1rem" }}>
            {mode === "create" ? "Create Ticket" : "Edit Ticket"}
          </h3>
          <button
            type="button"
            onClick={onCancel}
            aria-label="Close"
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              fontSize: "1.1rem",
              color: "var(--color-text-muted)",
              padding: "0.25rem",
            }}
          >
            ✕
          </button>
        </div>

        <form onSubmit={(e) => void handleSubmit(e)} noValidate>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.875rem" }}>
            {/* Title */}
            <div style={fieldStyle}>
              <label htmlFor="tf-title" style={labelStyle}>
                Title <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>*</span>
              </label>
              <input
                id="tf-title"
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                data-testid="ticket-form-title"
                style={inputStyle}
                placeholder="Short descriptive title"
                required
              />
              {errors.title && (
                <span data-testid="ticket-form-title-error" style={errorStyle}>
                  {errors.title}
                </span>
              )}
            </div>

            {/* Description */}
            <div style={fieldStyle}>
              <label htmlFor="tf-desc" style={labelStyle}>
                Description
              </label>
              <textarea
                id="tf-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                data-testid="ticket-form-description"
                style={{
                  ...inputStyle,
                  minHeight: "80px",
                  resize: "vertical",
                  fontFamily: "inherit",
                }}
                placeholder="Optional details…"
              />
            </div>

            {/* Status + Priority row */}
            <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
              <div style={{ ...fieldStyle, flex: 1, minWidth: "120px" }}>
                <label htmlFor="tf-status" style={labelStyle}>
                  Status
                </label>
                <select
                  id="tf-status"
                  value={status}
                  onChange={(e) => setStatus(e.target.value as TicketStatus)}
                  data-testid="ticket-form-status"
                  style={inputStyle}
                >
                  {TICKET_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {TICKET_STATUS_LABELS[s]}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ ...fieldStyle, flex: 1, minWidth: "120px" }}>
                <label htmlFor="tf-priority" style={labelStyle}>
                  Priority <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>*</span>
                </label>
                <select
                  id="tf-priority"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as TicketPriority)}
                  data-testid="ticket-form-priority"
                  style={inputStyle}
                >
                  {TICKET_PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {TICKET_PRIORITY_LABELS[p]}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Assignee */}
            <div style={fieldStyle}>
              <label htmlFor="tf-assignee" style={labelStyle}>
                Assignee (User ID)
              </label>
              <input
                id="tf-assignee"
                type="text"
                value={assignee}
                onChange={(e) => setAssignee(e.target.value)}
                data-testid="ticket-form-assignee"
                style={inputStyle}
                placeholder="Leave blank to leave unassigned"
              />
            </div>

            {/* Reporter */}
            <div style={fieldStyle}>
              <label htmlFor="tf-reporter" style={labelStyle}>
                Reporter <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>*</span>
              </label>
              <input
                id="tf-reporter"
                type="text"
                value={reporter}
                onChange={(e) => setReporter(e.target.value)}
                data-testid="ticket-form-reporter"
                style={inputStyle}
              />
              {errors.reporter && (
                <span data-testid="ticket-form-reporter-error" style={errorStyle}>
                  {errors.reporter}
                </span>
              )}
            </div>

            {/* Team */}
            <div style={fieldStyle}>
              <label htmlFor="tf-team" style={labelStyle}>
                Team <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>*</span>
              </label>
              <input
                id="tf-team"
                type="text"
                value={teamId}
                onChange={(e) => setTeamId(e.target.value)}
                data-testid="ticket-form-team"
                style={inputStyle}
                placeholder="Team ID"
              />
              {errors.teamId && (
                <span data-testid="ticket-form-team-error" style={errorStyle}>
                  {errors.teamId}
                </span>
              )}
            </div>

            {/* Estimate Points */}
            <div style={fieldStyle}>
              <label htmlFor="tf-points" style={labelStyle}>
                Estimate Points (optional)
              </label>
              <select
                id="tf-points"
                value={estimatePoints}
                onChange={(e) =>
                  setEstimatePoints(
                    e.target.value === ""
                      ? ""
                      : (Number(e.target.value) as EstimatePoints),
                  )
                }
                data-testid="ticket-form-estimate"
                style={inputStyle}
              >
                <option value="">Unestimated</option>
                {ESTIMATE_POINTS.map((p) => (
                  <option key={p} value={p}>
                    {p} {p === 1 ? "pt" : "pts"}
                  </option>
                ))}
              </select>
            </div>

            {/* RCDO link */}
            <div style={fieldStyle}>
              <label htmlFor="tf-rcdo" style={labelStyle}>
                RCDO Link (optional)
              </label>
              <input
                id="tf-rcdo"
                type="text"
                value={rcdoNodeId}
                onChange={(e) => setRcdoNodeId(e.target.value)}
                data-testid="ticket-form-rcdo"
                style={inputStyle}
                placeholder="RCDO node ID"
              />
            </div>

            {/* Target Week */}
            <div style={fieldStyle}>
              <label htmlFor="tf-target-week" style={labelStyle}>
                Target Week (optional)
              </label>
              <input
                id="tf-target-week"
                type="date"
                value={targetWeek}
                onChange={(e) => setTargetWeek(e.target.value)}
                data-testid="ticket-form-target-week"
                style={inputStyle}
              />
            </div>

            {/* Submit error */}
            {submitError && (
              <div
                role="alert"
                data-testid="ticket-form-submit-error"
                style={{
                  background: "#fef2f2",
                  border: "1px solid #fecaca",
                  borderRadius: "var(--border-radius)",
                  padding: "0.5rem 0.75rem",
                  fontSize: "0.8rem",
                  color: "var(--color-danger)",
                }}
              >
                {submitError}
              </div>
            )}

            {/* Actions */}
            <div
              style={{
                display: "flex",
                gap: "0.5rem",
                justifyContent: "flex-end",
                marginTop: "0.25rem",
              }}
            >
              <button
                type="button"
                onClick={onCancel}
                style={{
                  padding: "0.5rem 1rem",
                  border: "1px solid var(--color-border)",
                  borderRadius: "var(--border-radius)",
                  background: "var(--color-surface)",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  fontSize: "0.875rem",
                }}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                data-testid="ticket-form-submit"
                style={{
                  padding: "0.5rem 1.25rem",
                  border: "none",
                  borderRadius: "var(--border-radius)",
                  background: "var(--color-primary)",
                  color: "#fff",
                  cursor: saving ? "not-allowed" : "pointer",
                  fontFamily: "inherit",
                  fontSize: "0.875rem",
                  fontWeight: 600,
                }}
              >
                {saving
                  ? mode === "create"
                    ? "Creating…"
                    : "Saving…"
                  : mode === "create"
                    ? "Create Ticket"
                    : "Save Changes"}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
