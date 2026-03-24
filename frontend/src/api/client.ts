/**
 * Typed API client for the Weekly Commit Module backend.
 * Injects the host-supplied auth token on every request.
 */

export interface ApiError {
  readonly status: number;
  readonly message: string;
  readonly details?: unknown;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly details?: unknown;

  constructor(error: ApiError) {
    super(error.message);
    this.name = "ApiRequestError";
    this.status = error.status;
    this.details = error.details;
  }
}

export interface RequestOptions {
  readonly method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  readonly body?: unknown;
  readonly headers?: Record<string, string>;
  /** Milliseconds before the request times out (default: 30_000). */
  readonly timeoutMs?: number;
}

export interface ApiClientConfig {
  readonly baseUrl: string;
  /** Returns the current auth token (may change between calls). */
  getAuthToken(): string;
}

/**
 * Creates a typed API client bound to the given base URL and auth provider.
 * All errors are normalized to {@link ApiRequestError}.
 */
export function createApiClient(config: ApiClientConfig) {
  async function request<T>(
    path: string,
    options: RequestOptions = {},
  ): Promise<T> {
    const { method = "GET", body, headers = {}, timeoutMs = 30_000 } = options;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    const requestHeaders: Record<string, string> = {
      "Content-Type": "application/json",
      Authorization: `Bearer ${config.getAuthToken()}`,
      ...headers,
    };

    try {
      const fetchInit: RequestInit = {
        method,
        headers: requestHeaders,
        signal: controller.signal,
      };
      if (body !== undefined) {
        fetchInit.body = JSON.stringify(body);
      }
      const response = await fetch(`${config.baseUrl}${path}`, fetchInit);

      if (!response.ok) {
        let errorDetails: unknown;
        try {
          errorDetails = await response.json();
        } catch {
          errorDetails = undefined;
        }
        throw new ApiRequestError({
          status: response.status,
          message: `API request failed: ${response.status} ${response.statusText}`,
          details: errorDetails,
        });
      }

      // 204 No Content → return undefined cast to T
      if (response.status === 204) {
        return undefined as T;
      }

      return (await response.json()) as T;
    } catch (err) {
      if (err instanceof ApiRequestError) {
        throw err;
      }
      if (err instanceof DOMException && err.name === "AbortError") {
        throw new ApiRequestError({
          status: 408,
          message: "Request timed out",
        });
      }
      throw new ApiRequestError({
        status: 0,
        message: err instanceof Error ? err.message : "Network error",
      });
    } finally {
      clearTimeout(timeoutId);
    }
  }

  return {
    get<T>(path: string, options?: Omit<RequestOptions, "method" | "body">) {
      return request<T>(path, { ...options, method: "GET" });
    },
    post<T>(
      path: string,
      body: unknown,
      options?: Omit<RequestOptions, "method" | "body">,
    ) {
      return request<T>(path, { ...options, method: "POST", body });
    },
    put<T>(
      path: string,
      body: unknown,
      options?: Omit<RequestOptions, "method" | "body">,
    ) {
      return request<T>(path, { ...options, method: "PUT", body });
    },
    patch<T>(
      path: string,
      body: unknown,
      options?: Omit<RequestOptions, "method" | "body">,
    ) {
      return request<T>(path, { ...options, method: "PATCH", body });
    },
    delete<T>(path: string, options?: Omit<RequestOptions, "method" | "body">) {
      return request<T>(path, { ...options, method: "DELETE" });
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
