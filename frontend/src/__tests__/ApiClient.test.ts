import { describe, it, expect, vi, beforeEach } from "vitest";
import { createApiClient, ApiRequestError } from "../api/client.js";

const BASE_URL = "http://localhost:8080/api";

function makeClient() {
  return createApiClient({
    baseUrl: BASE_URL,
    getAuthToken: () => "test-token",
  });
}

describe("ApiClient", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("attaches Authorization header on GET request", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), { status: 200 }),
    );

    const client = makeClient();
    await client.get("/plans");

    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe("Bearer test-token");
  });

  it("sends POST body as JSON", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "new-plan" }), { status: 201 }),
    );

    const client = makeClient();
    await client.post("/plans", { title: "Test Plan" });

    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(init.body).toBe(JSON.stringify({ title: "Test Plan" }));
    const headers = init.headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/json");
  });

  it("throws ApiRequestError on non-OK response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ message: "Not found" }), { status: 404 }),
    );

    const client = makeClient();
    await expect(client.get("/plans/missing")).rejects.toBeInstanceOf(
      ApiRequestError,
    );
  });

  it("includes status code in ApiRequestError", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ message: "Forbidden" }), { status: 403 }),
    );

    const client = makeClient();
    try {
      await client.get("/plans/secret");
    } catch (err) {
      expect(err).toBeInstanceOf(ApiRequestError);
      expect((err as ApiRequestError).status).toBe(403);
    }
  });

  it("returns undefined for 204 No Content", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 }),
    );

    const client = makeClient();
    const result = await client.delete("/plans/123");
    expect(result).toBeUndefined();
  });

  it("throws ApiRequestError on network failure", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(
      new TypeError("Failed to fetch"),
    );

    const client = makeClient();
    const err = await client.get("/plans").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).status).toBe(0);
  });

  it("uses correct HTTP method for PATCH", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ updated: true }), { status: 200 }),
    );

    const client = makeClient();
    await client.patch("/plans/123", { title: "Updated" });

    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("PATCH");
  });

  it("builds correct URL from base + path", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify([]), { status: 200 }),
    );

    const client = makeClient();
    await client.get("/tickets");

    const [url] = fetchSpy.mock.calls[0] as [string];
    expect(url).toBe(`${BASE_URL}/tickets`);
  });
});
