/**
 * Lightweight data-fetching hooks following a useQuery pattern.
 * Wraps the API client with loading and error state management.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { ApiRequestError } from "./client.js";

export interface QueryState<T> {
  readonly data: T | undefined;
  readonly loading: boolean;
  readonly error: ApiRequestError | null;
  /** Re-trigger the query. */
  refetch(): void;
}

/**
 * Executes an async fetcher and manages loading/error/data state.
 * Re-runs whenever the `key` string changes.
 *
 * @param key     Cache/identity key. Change it to re-run the query.
 * @param fetcher Async function returning the data.
 */
export function useQuery<T>(
  key: string,
  fetcher: () => Promise<T>,
  options?: { enabled?: boolean },
): QueryState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [revision, setRevision] = useState(0);
  const enabled = options?.enabled ?? true;

  // Keep a stable ref to the fetcher so the effect dep array stays clean
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    if (!enabled) {
      setData(undefined);
      setLoading(false);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    fetcherRef
      .current()
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          if (err instanceof ApiRequestError) {
            setError(err);
          } else {
            setError(
              new ApiRequestError({ status: 0, message: "Unexpected error" }),
            );
          }
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [enabled, key, revision]);

  const refetch = useCallback(() => {
    setRevision((r) => r + 1);
  }, []);

  return { data, loading, error, refetch };
}
