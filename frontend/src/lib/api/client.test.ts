import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { fieldError, problem } from "@/test/fixtures";
import { server } from "@/test/server";
import { apiRequest } from "./client";
import { ApiError } from "./errors";

async function caught(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError);
    return error as ApiError;
  }
  throw new Error("expected the request to fail");
}

describe("apiRequest (TS-FE-01)", () => {
  it("sends JSON to /api/v1 and returns the parsed body", async () => {
    let received: { method: string; contentType: string | null; body: unknown } | null = null;
    server.use(
      http.post("/api/v1/tickets", async ({ request }) => {
        received = {
          method: request.method,
          contentType: request.headers.get("Content-Type"),
          body: await request.json(),
        };
        return HttpResponse.json({ id: 7 }, { status: 201 });
      }),
    );

    const result = await apiRequest<{ id: number }>("POST", "/tickets", { title: "t" });

    expect(result).toEqual({ id: 7 });
    expect(received).toEqual({ method: "POST", contentType: "application/json", body: { title: "t" } });
  });

  it("turns application/problem+json into a typed ApiError", async () => {
    server.use(
      http.post("/api/v1/tickets", () =>
        problem(400, "VALIDATION_FAILED", { errors: [fieldError("title", "BLANK")] }),
      ),
    );

    const error = await caught(apiRequest("POST", "/tickets", {}));

    expect(error.status).toBe(400);
    expect(error.code).toBe("VALIDATION_FAILED");
    expect(error.correlationId).toBe("corr-123");
    expect(error.fieldErrors).toEqual([fieldError("title", "BLANK")]);
  });

  it("maps a non-JSON 5xx to INTERNAL_ERROR, keeping the correlation id header", async () => {
    server.use(
      http.get("/api/v1/tickets/1", () =>
        new HttpResponse("<html>Bad gateway</html>", {
          status: 502,
          headers: { "Content-Type": "text/html", "X-Correlation-Id": "gw-9" },
        }),
      ),
    );

    const error = await caught(apiRequest("GET", "/tickets/1"));

    expect(error.code).toBe("INTERNAL_ERROR");
    expect(error.status).toBe(502);
    expect(error.correlationId).toBe("gw-9");
    expect(error.message).not.toContain("Bad gateway");
  });

  it("maps an unexpected non-problem 4xx to UNEXPECTED_RESPONSE", async () => {
    server.use(http.get("/api/v1/tickets/1", () => HttpResponse.json({ error: "Not Found" }, { status: 404 })));

    const error = await caught(apiRequest("GET", "/tickets/1"));

    expect(error.code).toBe("UNEXPECTED_RESPONSE");
    expect(error.status).toBe(404);
  });

  it("maps a transport failure to NETWORK_ERROR", async () => {
    server.use(http.get("/api/v1/tickets/1", () => HttpResponse.error()));

    const error = await caught(apiRequest("GET", "/tickets/1"));

    expect(error.code).toBe("NETWORK_ERROR");
    expect(error.status).toBe(0);
  });
});
