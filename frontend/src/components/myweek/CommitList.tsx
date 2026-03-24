/**
 * CommitList — ordered list of weekly commits with drag-and-drop reorder.
 *
 * - Priority rank badge, chess piece icon, title, RCDO path, estimate, status.
 * - Drag-and-drop reorder via @dnd-kit/sortable (pointer + keyboard sensors).
 * - Move-up / Move-down buttons as WCAG 2.1 AA keyboard-accessible alternative.
 * - Inline expand/collapse to show description, success criteria, provenance.
 * - Edit and Delete actions (only enabled in DRAFT state).
 */
import { useState, useCallback, useMemo, useEffect } from "react";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { CommitResponse, PlanState, ChessPiece } from "../../api/planTypes.js";

// ── Chess piece metadata ──────────────────────────────────────────────────────

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};

const CHESS_PIECE_LABELS: Record<ChessPiece, string> = {
  KING: "King",
  QUEEN: "Queen",
  ROOK: "Rook",
  BISHOP: "Bishop",
  KNIGHT: "Knight",
  PAWN: "Pawn",
};

// ── SortableItem ──────────────────────────────────────────────────────────────

interface SortableItemProps {
  readonly commit: CommitResponse;
  readonly rank: number;
  readonly totalCount: number;
  readonly planState: PlanState;
  readonly rcdoLabel?: string;
  readonly isDraft: boolean;
  readonly onEdit: (commit: CommitResponse) => void;
  readonly onDelete: (commitId: string) => void;
  readonly onMoveUp: (commitId: string) => void;
  readonly onMoveDown: (commitId: string) => void;
  /** Optional: called when "View lineage" is clicked for a carry-forward commit. */
  readonly onViewLineage?: (commitId: string) => void;
  /** Optional: called to create a ticket from a commit. */
  readonly onCreateTicket?: (commit: CommitResponse) => void;
}

function SortableItem({
  commit,
  rank,
  totalCount,
  isDraft,
  rcdoLabel,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
  onViewLineage,
  onCreateTicket,
}: SortableItemProps) {
  const [expanded, setExpanded] = useState(false);

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: commit.id });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    position: "relative",
    zIndex: isDragging ? 1 : undefined,
  };

  const isFirst = rank === 1;
  const isLast = rank === totalCount;

  return (
    <li
      ref={setNodeRef}
      style={style}
      data-testid={`commit-item-${commit.id}`}
      aria-label={`Commit ${rank}: ${commit.title}`}
    >
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          padding: "0.75rem",
        }}
      >
        {/* Main row */}
        <div
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: "0.625rem",
            flexWrap: "wrap",
          }}
        >
          {/* Drag handle */}
          {isDraft && (
            <button
              type="button"
              {...attributes}
              {...listeners}
              aria-label={`Drag to reorder commit: ${commit.title}`}
              data-testid={`drag-handle-${commit.id}`}
              style={{
                background: "none",
                border: "none",
                cursor: "grab",
                color: "var(--color-text-muted)",
                fontSize: "1rem",
                padding: "0 2px",
                flexShrink: 0,
                touchAction: "none",
              }}
            >
              ⠿
            </button>
          )}

          {/* Priority rank badge */}
          <span
            data-testid={`priority-rank-${commit.id}`}
            style={{
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              width: "1.5rem",
              height: "1.5rem",
              borderRadius: "50%",
              background: "var(--color-primary)",
              color: "#fff",
              fontSize: "0.75rem",
              fontWeight: 700,
              flexShrink: 0,
            }}
          >
            {rank}
          </span>

          {/* Chess piece icon */}
          <span
            title={CHESS_PIECE_LABELS[commit.chessPiece]}
            aria-label={CHESS_PIECE_LABELS[commit.chessPiece]}
            data-testid={`chess-piece-icon-${commit.id}`}
            style={{ fontSize: "1.125rem", lineHeight: 1, flexShrink: 0 }}
          >
            {CHESS_PIECE_ICONS[commit.chessPiece]}
          </span>

          {/* Title */}
          <button
            type="button"
            onClick={() => setExpanded((e) => !e)}
            aria-expanded={expanded}
            data-testid={`commit-title-${commit.id}`}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              fontWeight: 600,
              fontSize: "0.9rem",
              textAlign: "left",
              padding: 0,
              flex: 1,
              color: "var(--color-text)",
              fontFamily: "inherit",
            }}
          >
            {commit.title}
          </button>

          {/* Metadata badges */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.375rem",
              flexShrink: 0,
              flexWrap: "wrap",
            }}
          >
            {commit.estimatePoints != null && (
              <span
                data-testid={`estimate-badge-${commit.id}`}
                style={{
                  fontSize: "0.75rem",
                  fontWeight: 700,
                  padding: "1px 7px",
                  borderRadius: "999px",
                  background: "#e0e7ff",
                  color: "#3730a3",
                }}
              >
                {commit.estimatePoints} pt
              </span>
            )}
            {commit.workItemId && (
              <span
                data-testid={`ticket-badge-${commit.id}`}
                style={{
                  fontSize: "0.75rem",
                  padding: "1px 7px",
                  borderRadius: "999px",
                  background: "#f3f4f6",
                  color: "var(--color-text-muted)",
                  fontFamily: "monospace",
                }}
              >
                🎫 {commit.workItemId}
              </span>
            )}
          </div>

          {/* Action buttons */}
          <div
            style={{
              display: "flex",
              gap: "0.25rem",
              flexShrink: 0,
            }}
          >
            {/* Move up / down — keyboard-accessible reorder */}
            {isDraft && (
              <>
                <button
                  type="button"
                  onClick={() => onMoveUp(commit.id)}
                  disabled={isFirst}
                  aria-label={`Move up: ${commit.title}`}
                  data-testid={`move-up-${commit.id}`}
                  style={{
                    background: "none",
                    border: "1px solid var(--color-border)",
                    borderRadius: "var(--border-radius)",
                    cursor: isFirst ? "not-allowed" : "pointer",
                    padding: "2px 6px",
                    fontSize: "0.75rem",
                    color: isFirst
                      ? "var(--color-text-muted)"
                      : "var(--color-text)",
                    opacity: isFirst ? 0.5 : 1,
                  }}
                >
                  ▲
                </button>
                <button
                  type="button"
                  onClick={() => onMoveDown(commit.id)}
                  disabled={isLast}
                  aria-label={`Move down: ${commit.title}`}
                  data-testid={`move-down-${commit.id}`}
                  style={{
                    background: "none",
                    border: "1px solid var(--color-border)",
                    borderRadius: "var(--border-radius)",
                    cursor: isLast ? "not-allowed" : "pointer",
                    padding: "2px 6px",
                    fontSize: "0.75rem",
                    color: isLast
                      ? "var(--color-text-muted)"
                      : "var(--color-text)",
                    opacity: isLast ? 0.5 : 1,
                  }}
                >
                  ▼
                </button>
              </>
            )}

            {/* Edit / Delete — DRAFT only */}
            {isDraft && (
              <>
                <button
                  type="button"
                  onClick={() => onEdit(commit)}
                  aria-label={`Edit commit: ${commit.title}`}
                  data-testid={`edit-commit-${commit.id}`}
                  style={{
                    background: "none",
                    border: "1px solid var(--color-border)",
                    borderRadius: "var(--border-radius)",
                    cursor: "pointer",
                    padding: "2px 8px",
                    fontSize: "0.75rem",
                    color: "var(--color-text)",
                  }}
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => onDelete(commit.id)}
                  aria-label={`Delete commit: ${commit.title}`}
                  data-testid={`delete-commit-${commit.id}`}
                  style={{
                    background: "none",
                    border: "1px solid var(--color-danger)",
                    borderRadius: "var(--border-radius)",
                    cursor: "pointer",
                    padding: "2px 8px",
                    fontSize: "0.75rem",
                    color: "var(--color-danger)",
                  }}
                >
                  Delete
                </button>
              </>
            )}
            {/* Create ticket action — available in DRAFT and LOCKED */}
            {onCreateTicket && (
              <button
                type="button"
                onClick={() => onCreateTicket(commit)}
                aria-label={`Create ticket from commit: ${commit.title}`}
                data-testid={`create-ticket-from-commit-${commit.id}`}
                style={{
                  background: "none",
                  border: "1px solid var(--color-border)",
                  borderRadius: "var(--border-radius)",
                  cursor: "pointer",
                  padding: "2px 8px",
                  fontSize: "0.75rem",
                  color: "var(--color-text-muted)",
                }}
                title="Create ticket from this commit"
              >
                🎫
              </button>
            )}
          </div>
        </div>

        {/* RCDO path */}
        {rcdoLabel && (
          <div
            data-testid={`rcdo-path-${commit.id}`}
            style={{
              marginTop: "0.375rem",
              marginLeft: isDraft ? "3rem" : "2.5rem",
              fontSize: "0.75rem",
              color: "var(--color-text-muted)",
            }}
          >
            🎯 {rcdoLabel}
          </div>
        )}

        {/* Inline expanded detail */}
        {expanded && (
          <div
            data-testid={`commit-detail-${commit.id}`}
            style={{
              marginTop: "0.75rem",
              paddingTop: "0.75rem",
              borderTop: "1px solid var(--color-border)",
              display: "flex",
              flexDirection: "column",
              gap: "0.5rem",
              fontSize: "0.875rem",
            }}
          >
            {commit.description && (
              <div>
                <span
                  style={{
                    fontWeight: 600,
                    color: "var(--color-text-muted)",
                    fontSize: "0.75rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}
                >
                  Description
                </span>
                <p
                  style={{ margin: "0.25rem 0 0", whiteSpace: "pre-wrap" }}
                  data-testid={`commit-description-${commit.id}`}
                >
                  {commit.description}
                </p>
              </div>
            )}

            {commit.successCriteria && (
              <div>
                <span
                  style={{
                    fontWeight: 600,
                    color: "var(--color-text-muted)",
                    fontSize: "0.75rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}
                >
                  Success Criteria
                </span>
                <p
                  style={{ margin: "0.25rem 0 0" }}
                  data-testid={`commit-sc-${commit.id}`}
                >
                  {commit.successCriteria}
                </p>
              </div>
            )}

            {commit.carryForwardStreak > 0 && (
              <div
                data-testid={`carry-forward-streak-${commit.id}`}
                style={{
                  padding: "0.375rem 0.625rem",
                  background: "#eff6ff",
                  borderRadius: "var(--border-radius)",
                  fontSize: "0.8rem",
                  color: "#1e40af",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: "0.5rem",
                }}
              >
                <span>
                  🔁 Carried forward {commit.carryForwardStreak} time
                  {commit.carryForwardStreak !== 1 ? "s" : ""}
                </span>
                {onViewLineage && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onViewLineage(commit.id);
                    }}
                    data-testid={`view-lineage-btn-${commit.id}`}
                    style={{
                      background: "none",
                      border: "1px solid #93c5fd",
                      borderRadius: "var(--border-radius)",
                      cursor: "pointer",
                      fontSize: "0.75rem",
                      color: "#1e40af",
                      padding: "1px 7px",
                      fontFamily: "inherit",
                    }}
                  >
                    View lineage
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

// ── CommitList ────────────────────────────────────────────────────────────────

export interface CommitListProps {
  /** Commits in priority order (sorted by priorityOrder ascending). */
  readonly commits: CommitResponse[];
  readonly planState: PlanState;
  /** Called with the new ordered list of commit IDs after a reorder. */
  readonly onReorder: (orderedIds: string[]) => void;
  readonly onEdit: (commit: CommitResponse) => void;
  readonly onDelete: (commitId: string) => void;
  /** Map of rcdoNodeId → display label (title) for rendered RCDO paths. */
  readonly rcdoLabels?: Record<string, string>;
  /** Optional: called to view carry-forward lineage for a commit. */
  readonly onViewLineage?: (commitId: string) => void;
  /** Optional: called to create a ticket pre-populated from a commit. */
  readonly onCreateTicket?: (commit: CommitResponse) => void;
}

export function CommitList({
  commits,
  planState,
  onReorder,
  onEdit,
  onDelete,
  rcdoLabels = {},
  onViewLineage,
  onCreateTicket,
}: CommitListProps) {
  const isDraft = planState === "DRAFT";

  // Local ordered state tracks optimistic reorder during drag-and-drop
  const [localCommits, setLocalCommits] = useState<CommitResponse[]>(
    () => [...commits].sort((a, b) => a.priorityOrder - b.priorityOrder),
  );

  // Stable key based on commit IDs only (not order). When the set of commits
  // changes (add / delete / server refetch with new IDs), reset local state.
  const incomingIdsKey = useMemo(
    () =>
      commits
        .map((c) => c.id)
        .slice()
        .sort()
        .join(","),
    [commits],
  );

  useEffect(() => {
    setLocalCommits(
      [...commits].sort((a, b) => a.priorityOrder - b.priorityOrder),
    );
    // We intentionally depend on the ID key, not the full commits array, so
    // that an optimistic reorder (same IDs, different order) doesn't get
    // overwritten by a stale prop update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [incomingIdsKey]);

  // DnD sensors: pointer for mouse/touch, keyboard for accessibility
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      if (!over || active.id === over.id) return;

      setLocalCommits((prev) => {
        const oldIndex = prev.findIndex((c) => c.id === active.id);
        const newIndex = prev.findIndex((c) => c.id === over.id);
        const reordered = arrayMove(prev, oldIndex, newIndex);
        onReorder(reordered.map((c) => c.id));
        return reordered;
      });
    },
    [onReorder],
  );

  const handleMoveUp = useCallback(
    (commitId: string) => {
      setLocalCommits((prev) => {
        const idx = prev.findIndex((c) => c.id === commitId);
        if (idx <= 0) return prev;
        const reordered = arrayMove(prev, idx, idx - 1);
        onReorder(reordered.map((c) => c.id));
        return reordered;
      });
    },
    [onReorder],
  );

  const handleMoveDown = useCallback(
    (commitId: string) => {
      setLocalCommits((prev) => {
        const idx = prev.findIndex((c) => c.id === commitId);
        if (idx < 0 || idx >= prev.length - 1) return prev;
        const reordered = arrayMove(prev, idx, idx + 1);
        onReorder(reordered.map((c) => c.id));
        return reordered;
      });
    },
    [onReorder],
  );

  if (localCommits.length === 0) {
    return (
      <div
        data-testid="commit-list-empty"
        style={{
          padding: "2rem",
          textAlign: "center",
          color: "var(--color-text-muted)",
          fontSize: "0.9rem",
        }}
      >
        {isDraft
          ? "No commits yet. Add your first commit for this week!"
          : "No commits in this plan."}
      </div>
    );
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragEnd={handleDragEnd}
    >
      <SortableContext
        items={localCommits.map((c) => c.id)}
        strategy={verticalListSortingStrategy}
      >
        <ol
          data-testid="commit-list"
          aria-label="Weekly commits"
          style={{
            listStyle: "none",
            margin: 0,
            padding: 0,
            display: "flex",
            flexDirection: "column",
            gap: "0.5rem",
          }}
        >
          {localCommits.map((commit, index) => {
            const rcdoLabel =
              commit.rcdoNodeId != null
                ? rcdoLabels[commit.rcdoNodeId]
                : undefined;
            return (
              <SortableItem
                key={commit.id}
                commit={commit}
                rank={index + 1}
                totalCount={localCommits.length}
                planState={planState}
                isDraft={isDraft}
                {...(rcdoLabel !== undefined ? { rcdoLabel } : {})}
                onEdit={onEdit}
                onDelete={onDelete}
                onMoveUp={handleMoveUp}
                onMoveDown={handleMoveDown}
                {...(onViewLineage ? { onViewLineage } : {})}
                {...(onCreateTicket ? { onCreateTicket } : {})}
              />
            );
          })}
        </ol>
      </SortableContext>
    </DndContext>
  );
}
