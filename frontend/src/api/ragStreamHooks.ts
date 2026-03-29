/**
 * React hook for streaming RAG queries via the SSE endpoint.
 *
 * Wraps streamRagQuery() with React state so components get incremental
 * answer updates as delta events arrive.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { streamRagQuery } from "./ragStreamApi.js";
import type { RagSource } from "./ragApi.js";

// ── Public types ──────────────────────────────────────────────────────────────

export interface StreamingRagState {
  /** Incrementally-built answer string. Empty string before streaming starts. */
  answer: string;
  /** Source citations. Populated after the "sources" event arrives. */
  sources: RagSource[];
  /** Confidence score [0–1]. Populated after the "confidence" event arrives. */
  confidence: number;
  /** True while the stream is open and receiving events. */
  isStreaming: boolean;
  /** Error message, or null if no error has occurred. */
  error: string | null;
  /**
   * Starts a new stream for the given question.
   * Cancels any in-flight stream before starting a new one.
   */
  startStream: (question: string) => void;
  /** Cancels the current stream (no-op if not streaming). */
  cancel: () => void;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Provides streaming RAG query state for a component.
 *
 * @param teamId  UUID string for the team scope (required by the backend).
 * @param userId  UUID string for the requesting user (for audit).
 */
export function useStreamingRagQuery(
  teamId: string,
  userId: string,
): StreamingRagState {
  const [answer, setAnswer] = useState("");
  const [sources, setSources] = useState<RagSource[]>([]);
  const [confidence, setConfidence] = useState(0);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Keep the current AbortController in a ref so it survives renders
  const abortRef = useRef<AbortController | null>(null);

  // Cleanup on unmount: abort any in-flight request
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
  }, []);

  const startStream = useCallback(
    (question: string) => {
      // Cancel any previous stream
      abortRef.current?.abort();
      abortRef.current = null;

      // Reset state for the new query
      setAnswer("");
      setSources([]);
      setConfidence(0);
      setError(null);
      setIsStreaming(true);

      const controller = streamRagQuery(question, teamId, userId, {
        onDelta: (token) => {
          setAnswer((prev) => prev + token);
        },
        onSources: (s) => {
          setSources(s);
        },
        onConfidence: (score) => {
          setConfidence(score);
        },
        onDone: () => {
          setIsStreaming(false);
        },
        onError: (msg) => {
          setError(msg);
          setIsStreaming(false);
        },
      });

      abortRef.current = controller;
    },
    [teamId, userId],
  );

  return { answer, sources, confidence, isStreaming, error, startStream, cancel };
}
