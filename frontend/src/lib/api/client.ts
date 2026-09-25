import { ApiError } from "./errors";
import type { Problem } from "./types";

export const API_BASE = "/api/v1";

type Method = "GET" | "POST" | "PATCH" | "PUT";

function toUrl(path: string): string {
  const relative = `${API_BASE}${path}`;
  // Absolute in the browser (and jsdom); relative paths are resolved against the page origin.
  return typeof window === "undefined" ? relative : new URL(relative, window.location.origin).toString();
}

function isProblem(value: unknown): value is Problem {
  return typeof value === "object" && value !== null && typeof (value as Problem).code === "string";
}

async function toApiError(response: Response): Promise<ApiError> {
  const correlationId = response.headers.get("X-Correlation-Id");
  const contentType = response.headers.get("Content-Type") ?? "";
  if (contentType.includes("json")) {
    try {
      const body: unknown = await response.json();
      if (isProblem(body)) {
        return new ApiError(response.status, body.code, body, body.correlationId ?? correlationId);
      }
    } catch {
      // Fall through to a generic error: never surface raw bodies.
    }
  }
  const code = response.status >= 500 ? "INTERNAL_ERROR" : "UNEXPECTED_RESPONSE";
  return new ApiError(response.status, code, null, correlationId);
}

/** Sends a JSON request to the backend (through the Next.js proxy) and returns the parsed body or throws ApiError. */
export async function apiRequest<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  let response: Response;
  try {
    response = await fetch(toUrl(path), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw ApiError.network();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}
