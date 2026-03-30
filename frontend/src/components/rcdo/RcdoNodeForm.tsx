/**
 * Create and edit form for RCDO nodes.
 */
import { useState, type FormEvent } from "react";
import { Button } from "../ui/Button.js";
import { Input } from "../ui/Input.js";
import { Select } from "../ui/Select.js";
import type { RcdoTreeNode, RcdoNodeType, RcdoNodeStatus, CreateRcdoNodePayload, UpdateRcdoNodePayload } from "../../api/rcdoTypes.js";

function flattenByType(nodes: RcdoTreeNode[], targetType: RcdoNodeType): RcdoTreeNode[] {
  const result: RcdoTreeNode[] = [];
  function collect(node: RcdoTreeNode) {
    if (node.nodeType === targetType && node.status !== "ARCHIVED") result.push(node);
    node.children.forEach(collect);
  }
  nodes.forEach(collect);
  return result;
}

function requiredParentType(nodeType: RcdoNodeType): RcdoNodeType | null {
  switch (nodeType) {
    case "RALLY_CRY": return null;
    case "DEFINING_OBJECTIVE": return "RALLY_CRY";
    case "OUTCOME": return "DEFINING_OBJECTIVE";
  }
}

const NODE_TYPE_LABELS: Record<RcdoNodeType, string> = {
  RALLY_CRY: "Rally Cry",
  DEFINING_OBJECTIVE: "Defining Objective",
  OUTCOME: "Outcome",
};

function mergeDescriptionWithTargetMetric(description: string, targetMetric: string): string | undefined {
  const trimmedDescription = description.trim();
  const trimmedTargetMetric = targetMetric.trim();
  if (!trimmedDescription && !trimmedTargetMetric) return undefined;
  if (!trimmedTargetMetric) return trimmedDescription;
  if (!trimmedDescription) return `Target metric: ${trimmedTargetMetric}`;
  return `${trimmedDescription}\n\nTarget metric: ${trimmedTargetMetric}`;
}

interface FormValues {
  title: string; description: string; ownerTeamId: string; ownerUserId: string; parentId: string; targetMetric: string;
}
type FormErrors = Partial<Record<keyof FormValues, string>>;

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
  readonly initialValues: { readonly title: string; readonly description?: string; readonly ownerTeamId?: string; readonly ownerUserId?: string; };
  readonly tree: RcdoTreeNode[];
  readonly onSubmit: (payload: UpdateRcdoNodePayload) => Promise<void>;
  readonly onCancel: () => void;
}
export type RcdoNodeFormProps = CreateModeProps | EditModeProps;

const textareaCls = "w-full rounded-default border border-border bg-surface px-3 py-1.5 text-sm resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:border-primary";

export function RcdoNodeForm(props: RcdoNodeFormProps) {
  const { nodeType, onCancel } = props;

  const [values, setValues] = useState<FormValues>({
    title: props.mode === "edit" ? props.initialValues.title : "",
    description: props.mode === "edit" ? (props.initialValues.description ?? "") : "",
    ownerTeamId: props.mode === "edit" ? (props.initialValues.ownerTeamId ?? "") : "",
    ownerUserId: props.mode === "edit" ? (props.initialValues.ownerUserId ?? "") : "",
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
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: undefined }));
  }

  function validate(): boolean {
    const next: FormErrors = {};
    if (!values.title.trim()) next.title = "Title is required";
    if (props.mode === "create" && parentType && !values.parentId) next.parentId = `A ${NODE_TYPE_LABELS[parentType]} parent is required`;
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true); setSubmitError(null);
    try {
      const mergedDescription = mergeDescriptionWithTargetMetric(values.description, values.targetMetric);
      if (props.mode === "create") {
        await props.onSubmit({
          nodeType,
          title: values.title.trim(),
          ...(mergedDescription ? { description: mergedDescription } : {}),
          ...(values.parentId ? { parentId: values.parentId } : {}),
          ...(values.ownerTeamId.trim() ? { ownerTeamId: values.ownerTeamId.trim() } : {}),
          ...(values.ownerUserId.trim() ? { ownerUserId: values.ownerUserId.trim() } : {}),
        });
      } else {
        await props.onSubmit({
          ...(values.title.trim() ? { title: values.title.trim() } : {}),
          ...(mergedDescription ? { description: mergedDescription } : {}),
          ...(values.ownerTeamId.trim() ? { ownerTeamId: values.ownerTeamId.trim() } : {}),
          ...(values.ownerUserId.trim() ? { ownerUserId: values.ownerUserId.trim() } : {}),
        });
      }
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "An unexpected error occurred");
    } finally {
      setSubmitting(false);
    }
  }

  const headingLabel = `${props.mode === "create" ? "Create" : "Edit"} ${NODE_TYPE_LABELS[nodeType]}`;

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={headingLabel} data-testid="rcdo-node-form">
      <h3 className="m-0 mb-4 text-lg font-semibold">{headingLabel}</h3>

      {props.mode === "edit" && (
        <div className="mb-4 text-sm text-muted">
          Status: <strong className="text-foreground">{props.status}</strong>
          <span className="ml-2 text-xs">(use Activate / Archive actions to change)</span>
        </div>
      )}

      {/* Parent selector */}
      {props.mode === "create" && parentType && (
        <div className="mb-4">
          <Select
            id="rcdo-form-parent"
            label={`Parent ${NODE_TYPE_LABELS[parentType]} *`}
            value={values.parentId}
            onChange={(e) => handleChange("parentId", e.target.value)}
            aria-required="true"
            error={errors.parentId}
          >
            <option value="">Select a {NODE_TYPE_LABELS[parentType]}…</option>
            {validParents.map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}
          </Select>
        </div>
      )}

      {/* Title */}
      <div className="mb-4">
        <Input id="rcdo-form-title" label="Title *" type="text" value={values.title} onChange={(e) => handleChange("title", e.target.value)} aria-required="true" error={errors.title} placeholder="Enter a clear, concise title" />
      </div>

      {/* Description */}
      <div className="mb-4 flex flex-col gap-1.5">
        <label htmlFor="rcdo-form-description" className="text-sm font-medium">Description</label>
        <textarea id="rcdo-form-description" value={values.description} onChange={(e) => handleChange("description", e.target.value)} rows={3} className={textareaCls} placeholder="Optional description" />
      </div>

      {/* Target Metric (Outcomes only) */}
      {nodeType === "OUTCOME" && (
        <div className="mb-4">
          <Input id="rcdo-form-target-metric" label="Target Metric" type="text" value={values.targetMetric} onChange={(e) => handleChange("targetMetric", e.target.value)} placeholder="e.g. Increase NPS from 30 to 50" />
          <p className="mt-1 text-xs text-muted">Displayed as planning context (stored in description when set).</p>
        </div>
      )}

      {/* Owner Team */}
      <div className="mb-4">
        <Input id="rcdo-form-owner-team" label="Owner Team" type="text" value={values.ownerTeamId} onChange={(e) => handleChange("ownerTeamId", e.target.value)} placeholder="Team ID (optional)" />
      </div>

      {/* Owner User */}
      <div className="mb-4">
        <Input id="rcdo-form-owner-user" label="Owner User" type="text" value={values.ownerUserId} onChange={(e) => handleChange("ownerUserId", e.target.value)} placeholder="User ID (optional)" />
      </div>

      {submitError && (
        <div role="alert" className="mb-4 rounded-default border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-foreground font-semibold">{submitError}</div>
      )}

      <div className="flex gap-2 justify-end">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>Cancel</Button>
        <Button type="submit" variant="primary" disabled={submitting}>
          {submitting ? "Saving…" : props.mode === "create" ? "Create" : "Save changes"}
        </Button>
      </div>
    </form>
  );
}
