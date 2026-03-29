/**
 * Tests for useStreamingRagQuery hook (ragStreamHooks.ts).
 *
 * The low-level streamRagQuery() is mocked so tests run without a real
 * network and can drive SSE events directly via the captured callbacks.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useStreamingRagQuery } from "../api/ragStreamHooks.js";
import * as ragStreamApi from "../api/ragStreamApi.js";
import type { StreamCallbacks } from "../api/ragStreamApi.js";

// ── Module mock ───────────────────────────────────────────────────────────────

vi.mock("../api/ragStreamApi.js", () => ({
  streamRagQuery: vi.fn(),
}));

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Captures the callbacks passed to streamRagQuery so tests can trigger them. */
function captureCallbacks(): { get: () => StreamCallbacks } {
  let captured: StreamCallbacks | null = null;
  vi.mocked(ragStreamApi.streamRagQuery).mockImplementation(
    (_question, _teamId, _userId, callbacks) => {
      captured = callbacks;
      return new AbortController();
    },
  );
  return {
    get: () => {
      if (!captured) throw new Error("streamRagQuery was not called yet");
      return captured;
    },
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("useStreamingRagQuery", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Idle state ──────────────────────────────────────────────────────────

  it("starts in idle state — empty answer, not streaming, no error", () => {
    vi.mocked(ragStreamApi.streamRagQuery).mockReturnValue(new AbortController());

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    expect(result.current.answer).toBe("");
    expect(result.current.sources).toEqual([]);
    expect(result.current.confidence).toBe(0);
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.error).toBeNull();
  });

  // ── State transitions ───────────────────────────────────────────────────

  it("transitions to streaming=true immediately after startStream()", () => {
    captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("What happened last week?");
    });

    expect(result.current.isStreaming).toBe(true);
    expect(result.current.answer).toBe("");
  });

  it("transitions to isStreaming=false after done event", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("question");
    });
    act(() => {
      cb.get().onDone();
    });

    expect(result.current.isStreaming).toBe(false);
  });

  it("full lifecycle: idle → streaming → done", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    // Idle
    expect(result.current.isStreaming).toBe(false);

    // Streaming
    act(() => {
      result.current.startStream("question");
    });
    expect(result.current.isStreaming).toBe(true);

    // Receive content
    act(() => {
      cb.get().onDelta("The ");
      cb.get().onDelta("answer.");
    });
    expect(result.current.answer).toBe("The answer.");

    // Done
    act(() => {
      cb.get().onDone();
    });
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.answer).toBe("The answer.");
  });

  // ── Delta accumulation ──────────────────────────────────────────────────

  it("appends delta tokens to answer string", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("question");
    });
    act(() => {
      cb.get().onDelta("Hello ");
      cb.get().onDelta("world");
      cb.get().onDelta("!");
    });

    expect(result.current.answer).toBe("Hello world!");
  });

  it("resets answer when a new stream starts", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("first question");
    });
    act(() => {
      cb.get().onDelta("Previous answer.");
      cb.get().onDone();
    });

    expect(result.current.answer).toBe("Previous answer.");

    // Start a new stream — answer should reset
    act(() => {
      result.current.startStream("second question");
    });

    expect(result.current.answer).toBe("");
    expect(result.current.isStreaming).toBe(true);
  });

  // ── Sources and confidence ──────────────────────────────────────────────

  it("populates sources from onSources callback", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("question");
    });
    act(() => {
      cb.get().onSources([
        { entityType: "commit", entityId: "abc", weekStartDate: "2025-01-06", snippet: "Deploy" },
      ]);
    });

    expect(result.current.sources).toHaveLength(1);
    expect(result.current.sources[0]?.entityId).toBe("abc");
  });

  it("sets confidence from onConfidence callback", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("question");
    });
    act(() => {
      cb.get().onConfidence(0.87);
    });

    expect(result.current.confidence).toBe(0.87);
  });

  // ── Error handling ──────────────────────────────────────────────────────

  it("sets error state and stops streaming on error event", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("question");
    });
    act(() => {
      cb.get().onError("AI unavailable");
    });

    expect(result.current.error).toBe("AI unavailable");
    expect(result.current.isStreaming).toBe(false);
  });

  it("clears previous error when a new stream starts", () => {
    const cb = captureCallbacks();

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("first");
    });
    act(() => {
      cb.get().onError("Previous error");
    });

    expect(result.current.error).toBe("Previous error");

    // New stream should clear the error
    act(() => {
      result.current.startStream("second");
    });

    expect(result.current.error).toBeNull();
  });

  // ── Cancel ──────────────────────────────────────────────────────────────

  it("cancel() aborts the AbortController and sets isStreaming=false", () => {
    const mockAbort = vi.fn();
    vi.mocked(ragStreamApi.streamRagQuery).mockReturnValue({
      abort: mockAbort,
      signal: new AbortController().signal,
    } as AbortController);

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    act(() => {
      result.current.startStream("question");
    });
    expect(result.current.isStreaming).toBe(true);

    act(() => {
      result.current.cancel();
    });

    expect(mockAbort).toHaveBeenCalledOnce();
    expect(result.current.isStreaming).toBe(false);
  });

  it("cancel() before streaming starts is a no-op", () => {
    vi.mocked(ragStreamApi.streamRagQuery).mockReturnValue(new AbortController());

    const { result } = renderHook(() =>
      useStreamingRagQuery("team-id", "user-id"),
    );

    // Should not throw
    act(() => {
      result.current.cancel();
    });

    expect(result.current.isStreaming).toBe(false);
  });
});
