/**
 * Modal confirmation dialog for archiving an RCDO node.
 * Shows an impact warning and blocks confirmation if the node has active children.
 */

interface ArchiveConfirmDialogProps {
  /** Display name of the node to be archived. */
  readonly nodeName: string;
  /**
   * True if the node has at least one non-archived child.
   * In this case the Archive button is disabled and a blocking message is shown.
   */
  readonly hasActiveChildren: boolean;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
  readonly submitting: boolean;
}

const overlayStyle: React.CSSProperties = {
  position: "fixed",
  inset: 0,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  backgroundColor: "rgba(0, 0, 0, 0.5)",
  zIndex: 1000,
};

const panelStyle: React.CSSProperties = {
  background: "var(--color-surface)",
  borderRadius: "var(--border-radius)",
  padding: "1.5rem",
  maxWidth: "480px",
  width: "90vw",
  boxShadow: "0 20px 60px rgba(0,0,0,0.3)",
};

export function ArchiveConfirmDialog({
  nodeName,
  hasActiveChildren,
  onConfirm,
  onCancel,
  submitting,
}: ArchiveConfirmDialogProps) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="archive-dialog-title"
      style={overlayStyle}
    >
      <div style={panelStyle}>
        <h3
          id="archive-dialog-title"
          style={{ margin: "0 0 1rem", fontSize: "1.125rem" }}
        >
          Archive "{nodeName}"?
        </h3>

        {hasActiveChildren ? (
          <p
            data-testid="archive-blocked-message"
            style={{
              margin: "0 0 1rem",
              color: "var(--color-danger)",
              fontSize: "0.9rem",
              padding: "0.75rem",
              background: "#fef2f2",
              borderRadius: "var(--border-radius)",
            }}
          >
            <strong>Cannot archive.</strong> This node has active child nodes.
            Archive all children first before archiving this node.
          </p>
        ) : (
          <p
            style={{
              margin: "0 0 1rem",
              color: "var(--color-text-muted)",
              fontSize: "0.9rem",
            }}
          >
            Archiving will hide this node from active planning views. Commits
            already linked to this node will retain their linkage, but the node
            will no longer be selectable for new commits.
          </p>
        )}

        <div
          style={{
            display: "flex",
            gap: "0.5rem",
            justifyContent: "flex-end",
          }}
        >
          <button
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
            onClick={onConfirm}
            disabled={hasActiveChildren || submitting}
            aria-disabled={hasActiveChildren || submitting}
            data-testid="archive-confirm-button"
            style={{
              padding: "0.5rem 1rem",
              border: "none",
              borderRadius: "var(--border-radius)",
              background:
                hasActiveChildren
                  ? "var(--color-border)"
                  : "var(--color-danger)",
              color: hasActiveChildren ? "var(--color-text-muted)" : "#fff",
              cursor:
                hasActiveChildren || submitting ? "not-allowed" : "pointer",
              fontSize: "inherit",
              fontFamily: "inherit",
            }}
          >
            {submitting ? "Archiving…" : "Archive"}
          </button>
        </div>
      </div>
    </div>
  );
}
