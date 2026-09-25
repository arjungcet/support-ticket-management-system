import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "@tests/support/server";
import { GET, PATCH, POST } from "@/app/api/[...path]/route";

const context = (...path: string[]) => ({ params: Promise.resolve({ path }) });

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("/api/* runtime proxy (architecture §2, §17; review M-3)", () => {
  it("reads BACKEND_URL on every request, not at build time", async () => {
    server.use(
      http.get("http://backend-a.test/api/v1/tickets", () => HttpResponse.json({ from: "a" })),
      http.get("http://backend-b.test/api/v1/tickets", () => HttpResponse.json({ from: "b" })),
    );

    vi.stubEnv("BACKEND_URL", "http://backend-a.test");
    const first = await GET(new Request("http://localhost:3000/api/v1/tickets"), context("v1", "tickets"));
    vi.stubEnv("BACKEND_URL", "http://backend-b.test/");
    const second = await GET(new Request("http://localhost:3000/api/v1/tickets"), context("v1", "tickets"));

    expect(await first.json()).toEqual({ from: "a" });
    expect(await second.json()).toEqual({ from: "b" });
  });

  it("forwards method, path, query string, body and API headers, and relays the response", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test");
    let received: Record<string, unknown> = {};
    server.use(
      http.post("http://backend.test/api/v1/tickets/42/comments", async ({ request }) => {
        const url = new URL(request.url);
        received = {
          query: url.search,
          contentType: request.headers.get("content-type"),
          correlationId: request.headers.get("x-correlation-id"),
          cookie: request.headers.get("cookie"),
          body: await request.json(),
        };
        return HttpResponse.json(
          { id: 9 },
          {
            status: 201,
            headers: { Location: "/api/v1/tickets/42/comments/9", "X-Correlation-Id": "abc-1", "Set-Cookie": "x=1" },
          },
        );
      }),
    );

    const response = await POST(
      new Request("http://localhost:3000/api/v1/tickets/42/comments?page=1&q=100%25", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Correlation-Id": "abc-1", Cookie: "session=secret" },
        body: JSON.stringify({ author: "a", body: "b" }),
      }),
      context("v1", "tickets", "42", "comments"),
    );

    expect(received).toEqual({
      query: "?page=1&q=100%25",
      contentType: "application/json",
      correlationId: "abc-1",
      cookie: null,
      body: { author: "a", body: "b" },
    });
    expect(response.status).toBe(201);
    expect(response.headers.get("location")).toBe("/api/v1/tickets/42/comments/9");
    expect(response.headers.get("x-correlation-id")).toBe("abc-1");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(await response.json()).toEqual({ id: 9 });
  });

  it("relays backend errors unchanged (status and problem+json body)", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test");
    server.use(
      http.patch("http://backend.test/api/v1/tickets/42", () =>
        HttpResponse.json({ code: "TICKET_CONCURRENT_MODIFICATION" }, {
          status: 409,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    const response = await PATCH(
      new Request("http://localhost:3000/api/v1/tickets/42", { method: "PATCH", body: "{}" }),
      context("v1", "tickets", "42"),
    );

    expect(response.status).toBe(409);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
    expect(await response.json()).toEqual({ code: "TICKET_CONCURRENT_MODIFICATION" });
  });

  it("fails loudly instead of guessing a backend when BACKEND_URL is not configured", async () => {
    vi.stubEnv("BACKEND_URL", "");
    const errorLog = vi.spyOn(console, "error").mockImplementation(() => {});

    const response = await GET(new Request("http://localhost:3000/api/v1/tickets"), context("v1", "tickets"));

    expect(response.status).toBe(500);
    expect(errorLog).toHaveBeenCalledWith(expect.stringContaining("BACKEND_URL"));
    errorLog.mockRestore();
  });

  it("answers 502 when the backend is unreachable", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test");
    server.use(http.get("http://backend.test/api/v1/tickets", () => HttpResponse.error()));

    const response = await GET(new Request("http://localhost:3000/api/v1/tickets"), context("v1", "tickets"));

    expect(response.status).toBe(502);
  });
});
