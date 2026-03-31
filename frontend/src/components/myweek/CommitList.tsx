/**
 * CommitList — ordered list of weekly commits with drag-and-drop reorder.
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
import { GripVertical, ChevronUp, ChevronDown, Pencil, Trash2, Ticket, Target, ListTodo, RefreshCw } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { EmptyState } from "../shared/EmptyState.js";
import { cn } from "../../lib/utils.js";
import type { CommitResponse, PlanState, ChessPiece } from "../../api/planTypes.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙",
};
const CHESS_PIECE_LABELS: Record<ChessPiece, string> = {
  KING: "King", QUEEN: "Queen", ROOK: "Rook", BISHOP: "Bishop", KNIGHT: "Knight", PAWN: "Pawn",
};

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
  readonly onViewLineage?: (commitId: string) => void;
  readonly onCreateTicket?: (commit: CommitResponse) => void;
}

function SortableItem({ commit, rank, totalCount, isDraft, rcdoLabel, onEdit, onDelete, onMoveUp, onMoveDown, onViewLineage, onCreateTicket }: SortableItemProps) {
  const [expanded, setExpanded] = useState(false);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: commit.id });

  const isFirst = rank === 1;
  const isLast = rank === totalCount;

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.5 : 1, position: "relative", zIndex: isDragging ? 1 : undefined }}
      data-testid={`commit-item-${commit.id}`}
      aria-label={`Commit ${rank}: ${commit.title}`}
    >
      <div className="rounded-default border border-border bg-surface p-3">
        {/* Main row */}
        <div className="flex items-start gap-2 flex-wrap">
          {/* Drag handle */}
          {isDraft && (
            <button
              type="button"
              {...attributes}
              {...listeners}
              aria-label={`Drag to reorder commit: ${commit.title}`}
              data-testid={`drag-handle-${commit.id}`}
              className="text-muted hover:text-foreground cursor-grab shrink-0 p-0.5 touch-none"
            >
              <GripVertical className="h-4 w-4" />
            </button>
          )}

          {/* Priority rank badge */}
          <span
            data-testid={`priority-rank-${commit.id}`}
            className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-bold shrink-0"
          >
            {rank}
          </span>

          {/* Chess piece icon */}
          <span
            title={CHESS_PIECE_LABELS[commit.chessPiece]}
            aria-label={CHESS_PIECE_LABELS[commit.chessPiece]}
            data-testid={`chess-piece-icon-${commit.id}`}
            className="text-lg leading-none shrink-0 select-none"
          >
            {CHESS_PIECE_ICONS[commit.chessPiece]}
          </span>

          {/* Title */}
          <button
            type="button"
            onClick={() => setExpanded((e) => !e)}
            aria-expanded={expanded}
            data-testid={`commit-title-${commit.id}`}
            className="flex-1 bg-transparent border-none cursor-pointer font-semibold font-mono text-sm text-left p-0 text-foreground hover:text-primary transition-colors"
          >
            {commit.title}
          </button>

          {/* Metadata badges */}
          <div className="flex items-center gap-1.5 shrink-0 flex-wrap">
            {commit.estimatePoints != null && (
              <Badge data-testid={`estimate-badge-${commit.id}`} variant="primary">
                {commit.estimatePoints} pt
              </Badge>
            )}
            {commit.workItemId && (
              <Badge data-testid={`ticket-badge-${commit.id}`} variant="default">
                <Ticket className="h-3 w-3" />{commit.workItemKey ?? commit.workItemId}
              </Badge>
            )}
          </div>

          {/* Action buttons */}
          <div className="flex gap-1 shrink-0">
            {isDraft && (
              <>
                <Button variant="ghost" size="icon" onClick={() => onMoveUp(commit.id)} disabled={isFirst} aria-label={`Move up: ${commit.title}`} data-testid={`move-up-${commit.id}`} className="h-7 w-7">
                  <ChevronUp className="h-3.5 w-3.5" />
                </Button>
                <Button variant="ghost" size="icon" onClick={() => onMoveDown(commit.id)} disabled={isLast} aria-label={`Move down: ${commit.title}`} data-testid={`move-down-${commit.id}`} className="h-7 w-7">
                  <ChevronDown className="h-3.5 w-3.5" />
                </Button>
                <Button variant="secondary" size="sm" onClick={() => onEdit(commit)} aria-label={`Edit commit: ${commit.title}`} data-testid={`edit-commit-${commit.id}`} className="h-7 px-2 text-xs">
                  <Pencil className="h-3 w-3" />Edit
                </Button>
                <Button variant="danger" size="sm" onClick={() => onDelete(commit.id)} aria-label={`Delete commit: ${commit.title}`} data-testid={`delete-commit-${commit.id}`} className="h-7 px-2 text-xs">
                  <Trash2 className="h-3 w-3" />
                </Button>
              </>
            )}
            {onCreateTicket && (
              <Button variant="ghost" size="sm" onClick={() => onCreateTicket(commit)} aria-label={`Create ticket from commit: ${commit.title}`} data-testid={`create-ticket-from-commit-${commit.id}`} className="h-7 px-2 text-xs" title="Create ticket from this commit">
                <Ticket className="h-3 w-3" />
              </Button>
            )}
          </div>
        </div>

        {/* RCDO path */}
        {rcdoLabel && (
          <div data-testid={`rcdo-path-${commit.id}`} className={cn("mt-1.5 text-xs font-mono text-muted flex items-center gap-1", isDraft ? "ml-12" : "ml-10")}>
            <Target className="h-3 w-3 shrink-0" aria-hidden="true" />{rcdoLabel}
          </div>
        )}

        {/* Inline expanded detail */}
        {expanded && (
          <div data-testid={`commit-detail-${commit.id}`} className="mt-3 pt-3 border-t border-border flex flex-col gap-2 text-sm">
            {commit.description && (
              <div>
                <span className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1">Description</span>
                <p className="m-0 whitespace-pre-wrap" data-testid={`commit-description-${commit.id}`}>{commit.description}</p>
              </div>
            )}
            {commit.successCriteria && (
              <div>
                <span className="block text-xs font-semibold uppercase tracking-wider text-muted mb-1">Success Criteria</span>
                <p className="m-0" data-testid={`commit-sc-${commit.id}`}>{commit.successCriteria}</p>
              </div>
            )}
            {commit.carryForwardStreak > 0 && (
              <div data-testid={`carry-forward-streak-${commit.id}`} className="flex items-center justify-between gap-2 rounded-default bg-foreground/5 px-3 py-1.5 text-xs text-foreground">
                <span className="inline-flex items-center gap-1"><RefreshCw className="h-3 w-3" aria-hidden="true" />Carried forward {commit.carryForwardStreak} time{commit.carryForwardStreak !== 1 ? "s" : ""}</span>
                {onViewLineage && (
                  <Button variant="ghost" size="sm" onClick={(e) => { e.stopPropagation(); onViewLineage(commit.id); }} data-testid={`view-lineage-btn-${commit.id}`} className="h-6 px-2 text-xs border border-border text-foreground hover:bg-foreground/8">
                    View lineage
                  </Button>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

export interface CommitListProps {
  readonly commits: CommitResponse[];
  readonly planState: PlanState;
  readonly onReorder: (orderedIds: string[]) => void;
  readonly onEdit: (commit: CommitResponse) => void;
  readonly onDelete: (commitId: string) => void;
  readonly rcdoLabels?: Record<string, string>;
  readonly onViewLineage?: (commitId: string) => void;
  readonly onCreateTicket?: (commit: CommitResponse) => void;
  readonly onAddCommit?: () => void;
}

export function CommitList({ commits, planState, onReorder, onEdit, onDelete, rcdoLabels = {}, onViewLineage, onCreateTicket, onAddCommit }: CommitListProps) {
  const isDraft = planState === "DRAFT";
  const [localCommits, setLocalCommits] = useState<CommitResponse[]>(() => [...commits].sort((a, b) => a.priorityOrder - b.priorityOrder));

  const incomingIdsKey = useMemo(() => commits.map((c) => c.id).slice().sort().join(","), [commits]);
  useEffect(() => {
    setLocalCommits([...commits].sort((a, b) => a.priorityOrder - b.priorityOrder));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [incomingIdsKey]);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    setLocalCommits((prev) => {
      const oldIndex = prev.findIndex((c) => c.id === active.id);
      const newIndex = prev.findIndex((c) => c.id === over.id);
      const reordered = arrayMove(prev, oldIndex, newIndex);
      onReorder(reordered.map((c) => c.id));
      return reordered;
    });
  }, [onReorder]);

  const handleMoveUp = useCallback((commitId: string) => {
    setLocalCommits((prev) => {
      const idx = prev.findIndex((c) => c.id === commitId);
      if (idx <= 0) return prev;
      const reordered = arrayMove(prev, idx, idx - 1);
      onReorder(reordered.map((c) => c.id));
      return reordered;
    });
  }, [onReorder]);

  const handleMoveDown = useCallback((commitId: string) => {
    setLocalCommits((prev) => {
      const idx = prev.findIndex((c) => c.id === commitId);
      if (idx < 0 || idx >= prev.length - 1) return prev;
      const reordered = arrayMove(prev, idx, idx + 1);
      onReorder(reordered.map((c) => c.id));
      return reordered;
    });
  }, [onReorder]);

  if (localCommits.length === 0) {
    return (
      <EmptyState
        data-testid="commit-list-empty"
        icon={<ListTodo className="h-9 w-9" />}
        title={isDraft ? "No commits yet" : "No commits in this plan."}
        {...(isDraft
          ? {
              description: "Add your first commitment to start planning your week.",
            }
          : {})}
        {...(isDraft && onAddCommit
          ? {
              action: (
                <Button variant="primary" onClick={onAddCommit} data-testid="commit-list-add-btn">
                  + Add Commit
                </Button>
              ),
            }
          : {})}
      />
    );
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={localCommits.map((c) => c.id)} strategy={verticalListSortingStrategy}>
        <ol data-testid="commit-list" aria-label="Weekly commits" className="list-none m-0 p-0 flex flex-col gap-2">
          {localCommits.map((commit, index) => {
            const rcdoLabel = commit.rcdoNodeId != null ? rcdoLabels[commit.rcdoNodeId] : undefined;
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
