/**
 * Low-level SSE streaming client for the RAG query endpoint.
 *
 * Uses fetch() + ReadableStream instead of EventSource so we have full control
 * over the connection lifecycle (abort, reconnect, custom headers) and so the
 * same module can be upgraded to POST later without API changes.
 *
 * Event types emitted by the backend:
 *  - "delta"      — incremental answer text token
 *  - "sources"    — JSON array of RagSource objects (sent after last delta)
 *  - "confidence" — numeric confidence score as a string
 *  - "done"       — empty payload, signals stream end
 *  - "error"      — error message from the backend
 */
import type { RagSource } from "./ragApi.js";

export type { RagSource };

export interface StreamCallbacks {
  /** Called for each incremental text token. */
  onDelta: (text: string) => void;
  /** Called once with the full sources array after the answer completes. */
  onSources: (sources: RagSource[]) => void;
  /** Called once with the confidence score. */
  onConfidence: (score: number) => void;
  /** Called when the stream ends successfully. */
  onDone: () => void;
  /** Called on network error or backend error event. */
  onError: (message: string) => void;
}

/**
 * Starts a streaming RAG query against `/api/ai/rag/stream` using the
 * Server-Sent Events protocol over a `fetch()` GET request.
 *
 * @returns An `AbortController` whose `.abort()` method cancels the stream.
 */
export function streamRagQuery(
  question: string,
  teamId: string,
  userId: string,
  callbacks: StreamCallbacks,
): AbortController {
  const controller = new AbortController();

  const url = new URL("/api/ai/rag/stream", window.location.origin);
  url.searchParams.set("question", question);
  url.searchParams.set("teamId", teamId);
  url.searchParams.set("userId", userId);

  void (async () => {
    try {
      const response = await fetch(url.toString(), {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          "Cache-Control": "no-cache",
        },
        signal: controller.signal,
      });

      if (!response.ok) {
        callbacks.onError(
          `Server returned ${response.status}: ${response.statusText}`,
        );
        return;
      }

      if (!response.body) {
        callbacks.onError("Response body is null");
        return;
      }

      await parseEventStream(response.body, callbacks, controller.signal);
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") {
        // Intentional cancellation — not an error
        return;
      }
      callbacks.onError(err instanceof Error ? err.message : "Stream error");
    }
  })();

  return controller;
}

// ── SSE parsing ───────────────────────────────────────────────────────────────

/**
 * Reads a `ReadableStream<Uint8Array>` as newline-delimited SSE text and
 * dispatches events to the provided callbacks.
 *
 * The SSE format produced by Spring MVC SseEmitter is:
 *   event:<name>\ndata:<value>\n\n
 */
async function parseEventStream(
  body: ReadableStream<Uint8Array>,
  callbacks: StreamCallbacks,
  signal: AbortSignal,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // SSE events are separated by blank lines (\n\n)
      const events = buffer.split("\n\n");
      // Keep the last (possibly incomplete) fragment in the buffer
      buffer = events.pop() ?? "";

      for (const raw of events) {
        if (raw.trim() === "") continue;
        dispatchEvent(raw, callbacks);
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/** Parses a single SSE event block and calls the appropriate callback. */
function dispatchEvent(raw: string, callbacks: StreamCallbacks): void {
  let eventName = "";
  let data = "";

  for (const line of raw.split("\n")) {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      data = line.slice(5).trim();
    }
  }

  switch (eventName) {
    case "delta":
      callbacks.onDelta(data);
      break;

    case "sources": {
      try {
        const parsed = JSON.parse(data) as RagSource[];
        callbacks.onSources(parsed);
      } catch {
        callbacks.onSources([]);
      }
      break;
    }

    case "confidence": {
      const score = parseFloat(data);
      callbacks.onConfidence(isNaN(score) ? 0 : score);
      break;
    }

    case "done":
      callbacks.onDone();
      break;

    case "error":
      callbacks.onError(data || "Unknown error from stream");
      break;

    default:
      // Unknown or comment event — silently ignore
      break;
  }
}
