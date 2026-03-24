/**
 * Create and edit form for RCDO nodes.
 * - Create mode: nodeType and optional defaultParentId are pre-set; parent selector
 *   is shown for DOs (must pick a Rally Cry) and Outcomes (must pick a DO).
 * - Edit mode: title/description/owner fields are pre-filled; parent selector hidden.
 */
import { useState, type FormEvent } from "react";
import type {
  RcdoTreeNode,
  RcdoNodeType,
  RcdoNodeStatus,
  CreateRcdoNodePayload,
  UpdateRcdoNodePayload,
} from "../../api/rcdoTypes.js";

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Flatten tree to find all nodes of a given type (active or draft). */
function flattenByType(
  nodes: RcdoTreeNode[],
  targetType: RcdoNodeType,
): RcdoTreeNode[] {
  const result: RcdoTreeNode[] = [];
  function collect(node: RcdoTreeNode) {
    if (node.nodeType === targetType && node.status !== "ARCHIVED") {
      result.push(node);
    }
    node.children.forEach(collect);
  }
  nodes.forEach(collect);
  return result;
}

/** Returns the parent type required for a given node type, or null for Rally Cries. */
function requiredParentType(nodeType: RcdoNodeType): RcdoNodeType | null {
  switch (nodeType) {
    case "RALLY_CRY":
      return null;
    case "DEFINING_OBJECTIVE":
      return "RALLY_CRY";
    case "OUTCOME":
      return "DEFINING_OBJECTIVE";
  }
}

const NODE_TYPE_LABELS: Record<RcdoNodeType, string> = {
  RALLY_CRY: "Rally Cry",
  DEFINING_OBJECTIVE: "Defining Objective",
  OUTCOME: "Outcome",
};

function mergeDescriptionWithTargetMetric(
  description: string,
  targetMetric: string,
): string | undefined {
  const trimmedDescription = description.trim();
  const trimmedTargetMetric = targetMetric.trim();

  if (!trimmedDescription && !trimmedTargetMetric) {
    return undefined;
  }
  if (!trimmedTargetMetric) {
    return trimmedDescription;
  }
  if (!trimmedDescription) {
    return `Target metric: ${trimmedTargetMetric}`;
  }
  return `${trimmedDescription}\n\nTarget metric: ${trimmedTargetMetric}`;
}

// ── Form field shape ──────────────────────────────────────────────────────────

interface FormValues {
  title: string;
  description: string;
  ownerTeamId: string;
  ownerUserId: string;
  parentId: string;
  /** Outcome-only field (UI only; not persisted as a separate column). */
  targetMetric: string;
}

type FormErrors = Partial<Record<keyof FormValues, string>>;

// ── Props ─────────────────────────────────────────────────────────────────────

interface CreateModeProps {
  readonly mode: "create";
  readonly nodeType: RcdoNodeType;
  readonly defaultParentId?: string;
  readonly tree: RcdoTreeNode[];
  readonly onSubmit: (payload: CreateRcdoNodePayload) => Promise<void>;
  readonly onCancel: () => void;
}

interface EditModeProps {
  readonly mode: "edit";
  readonly nodeType: RcdoNodeType;
  readonly status: RcdoNodeStatus;
  readonly initialValues: {
    readonly title: string;
    readonly description?: string;
    readonly ownerTeamId?: string;
    readonly ownerUserId?: string;
  };
  readonly tree: RcdoTreeNode[];
  readonly onSubmit: (payload: UpdateRcdoNodePayload) => Promise<void>;
  readonly onCancel: () => void;
}

export type RcdoNodeFormProps = CreateModeProps | EditModeProps;

// ── RcdoNodeForm ──────────────────────────────────────────────────────────────

export function RcdoNodeForm(props: RcdoNodeFormProps) {
  const { nodeType, onCancel } = props;

  const [values, setValues] = useState<FormValues>({
    title: props.mode === "edit" ? props.initialValues.title : "",
    description:
      props.mode === "edit" ? (props.initialValues.description ?? "") : "",
    ownerTeamId:
      props.mode === "edit" ? (props.initialValues.ownerTeamId ?? "") : "",
    ownerUserId:
      props.mode === "edit" ? (props.initialValues.ownerUserId ?? "") : "",
    parentId: props.mode === "create" ? (props.defaultParentId ?? "") : "",
    targetMetric: "",
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const parentType = requiredParentType(nodeType);
  const validParents = parentType ? flattenByType(props.tree, parentType) : [];

  function handleChange(field: keyof FormValues, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  }

  function validate(): boolean {
    const next: FormErrors = {};
    if (!values.title.trim()) {
      next.title = "Title is required";
    }
    if (props.mode === "create" && parentType && !values.parentId) {
      next.parentId = `A ${NODE_TYPE_LABELS[parentType]} parent is required`;
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const mergedDescription = mergeDescriptionWithTargetMetric(
        values.description,
        values.targetMetric,
      );

      if (props.mode === "create") {
        const payload: CreateRcdoNodePayload = {
          nodeType,
          title: values.title.trim(),
          ...(mergedDescription ? { description: mergedDescription } : {}),
          ...(values.parentId ? { parentId: values.parentId } : {}),
          ...(values.ownerTeamId.trim()
            ? { ownerTeamId: values.ownerTeamId.trim() }
            : {}),
          ...(values.ownerUserId.trim()
            ? { ownerUserId: values.ownerUserId.trim() }
            : {}),
        };
        await props.onSubmit(payload);
      } else {
        const payload: UpdateRcdoNodePayload = {
          ...(values.title.trim() ? { title: values.title.trim() } : {}),
          ...(mergedDescription ? { description: mergedDescription } : {}),
          ...(values.ownerTeamId.trim()
            ? { ownerTeamId: values.ownerTeamId.trim() }
            : {}),
          ...(values.ownerUserId.trim()
            ? { ownerUserId: values.ownerUserId.trim() }
            : {}),
        };
        await props.onSubmit(payload);
      }
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : "An unexpected error occurred",
      );
    } finally {
      setSubmitting(false);
    }
  }

  const inputStyle: React.CSSProperties = {
    width: "100%",
    padding: "0.5rem",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--border-radius)",
    fontSize: "inherit",
    boxSizing: "border-box",
    fontFamily: "inherit",
  };

  const errorInputStyle: React.CSSProperties = {
    ...inputStyle,
    border: "1px solid var(--color-danger)",
  };

  const fieldStyle: React.CSSProperties = { marginBottom: "1rem" };

  const labelStyle: React.CSSProperties = {
    display: "block",
    marginBottom: "0.25rem",
    fontWeight: 600,
    fontSize: "0.875rem",
  };

  const errorTextStyle: React.CSSProperties = {
    color: "var(--color-danger)",
    fontSize: "0.75rem",
    marginTop: "0.25rem",
    display: "block",
  };

  const headingLabel = `${props.mode === "create" ? "Create" : "Edit"} ${NODE_TYPE_LABELS[nodeType]}`;

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-label={headingLabel}
      data-testid="rcdo-node-form"
    >
      <h3 style={{ margin: "0 0 1rem", fontSize: "1.125rem" }}>
        {headingLabel}
      </h3>

      {/* Read-only status display in edit mode */}
      {props.mode === "edit" && (
        <div
          style={{
            marginBottom: "1rem",
            fontSize: "0.875rem",
            color: "var(--color-text-muted)",
          }}
        >
          Status:{" "}
          <strong style={{ color: "var(--color-text)" }}>{props.status}</strong>
          <span style={{ marginLeft: "0.5rem", fontSize: "0.75rem" }}>
            (use Activate / Archive actions to change)
          </span>
        </div>
      )}

      {/* Parent selector — create mode only, not for Rally Cries */}
      {props.mode === "create" && parentType && (
        <div style={fieldStyle}>
          <label
            htmlFor="rcdo-form-parent"
            style={labelStyle}
          >
            Parent {NODE_TYPE_LABELS[parentType]}{" "}
            <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
              *
            </span>
          </label>
          <select
            id="rcdo-form-parent"
            value={values.parentId}
            onChange={(e) => handleChange("parentId", e.target.value)}
            aria-required="true"
            aria-describedby={
              errors.parentId ? "rcdo-form-parent-error" : undefined
            }
            style={errors.parentId ? errorInputStyle : inputStyle}
          >
            <option value="">Select a {NODE_TYPE_LABELS[parentType]}…</option>
            {validParents.map((p) => (
              <option key={p.id} value={p.id}>
                {p.title}
              </option>
            ))}
          </select>
          {errors.parentId && (
            <span id="rcdo-form-parent-error" role="alert" style={errorTextStyle}>
              {errors.parentId}
            </span>
          )}
        </div>
      )}

      {/* Title (required) */}
      <div style={fieldStyle}>
        <label htmlFor="rcdo-form-title" style={labelStyle}>
          Title{" "}
          <span aria-hidden="true" style={{ color: "var(--color-danger)" }}>
            *
          </span>
        </label>
        <input
          id="rcdo-form-title"
          type="text"
          value={values.title}
          onChange={(e) => handleChange("title", e.target.value)}
          aria-required="true"
          aria-describedby={
            errors.title ? "rcdo-form-title-error" : undefined
          }
          style={errors.title ? errorInputStyle : inputStyle}
          placeholder="Enter a clear, concise title"
        />
        {errors.title && (
          <span id="rcdo-form-title-error" role="alert" style={errorTextStyle}>
            {errors.title}
          </span>
        )}
      </div>

      {/* Description */}
      <div style={fieldStyle}>
        <label htmlFor="rcdo-form-description" style={labelStyle}>
          Description
        </label>
        <textarea
          id="rcdo-form-description"
          value={values.description}
          onChange={(e) => handleChange("description", e.target.value)}
          rows={3}
          style={{ ...inputStyle, resize: "vertical" }}
          placeholder="Optional description"
        />
      </div>

      {/* Target Metric — Outcomes only (UI display aid; not a separate backend column) */}
      {nodeType === "OUTCOME" && (
        <div style={fieldStyle}>
          <label htmlFor="rcdo-form-target-metric" style={labelStyle}>
            Target Metric
          </label>
          <input
            id="rcdo-form-target-metric"
            type="text"
            value={values.targetMetric}
            onChange={(e) => handleChange("targetMetric", e.target.value)}
            style={inputStyle}
            placeholder="e.g. Increase NPS from 30 to 50"
          />
          <span
            style={{
              fontSize: "0.75rem",
              color: "var(--color-text-muted)",
              marginTop: "0.25rem",
              display: "block",
            }}
          >
            Displayed as planning context (stored in description when set).
          </span>
        </div>
      )}

      {/* Owner Team ID */}
      <div style={fieldStyle}>
        <label htmlFor="rcdo-form-owner-team" style={labelStyle}>
          Owner Team
        </label>
        <input
          id="rcdo-form-owner-team"
          type="text"
          value={values.ownerTeamId}
          onChange={(e) => handleChange("ownerTeamId", e.target.value)}
          style={inputStyle}
          placeholder="Team ID (optional)"
        />
      </div>

      {/* Owner User ID */}
      <div style={fieldStyle}>
        <label htmlFor="rcdo-form-owner-user" style={labelStyle}>
          Owner User
        </label>
        <input
          id="rcdo-form-owner-user"
          type="text"
          value={values.ownerUserId}
          onChange={(e) => handleChange("ownerUserId", e.target.value)}
          style={inputStyle}
          placeholder="User ID (optional)"
        />
      </div>

      {/* API error */}
      {submitError && (
        <div
          role="alert"
          style={{
            color: "var(--color-danger)",
            marginBottom: "1rem",
            fontSize: "0.875rem",
            padding: "0.5rem",
            background: "#fef2f2",
            borderRadius: "var(--border-radius)",
          }}
        >
          {submitError}
        </div>
      )}

      {/* Action buttons */}
      <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
        <button
          type="button"
          onClick={onCancel}
          disabled={submitting}
          style={{
            padding: "0.5rem 1rem",
            border: "1px solid var(--color-border)",
            borderRadius: "var(--border-radius)",
            background: "var(--color-surface)",
            cursor: "pointer",
            fontSize: "inherit",
            fontFamily: "inherit",
          }}
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={submitting}
          style={{
            padding: "0.5rem 1rem",
            border: "none",
            borderRadius: "var(--border-radius)",
            background: "var(--color-primary)",
            color: "#fff",
            cursor: submitting ? "not-allowed" : "pointer",
            fontSize: "inherit",
            fontFamily: "inherit",
          }}
        >
          {submitting
            ? "Saving…"
            : props.mode === "create"
              ? "Create"
              : "Save changes"}
        </button>
      </div>
    </form>
  );
}
