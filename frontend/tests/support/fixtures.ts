import { HttpResponse } from "msw";
import type {
  CommentResponse,
  PageResponse,
  Problem,
  ProblemFieldError,
  TicketResponse,
  TicketStatus,
  TicketSummaryResponse,
} from "@/lib/api/types";

/** Fixtures follow spec/api-contract.md §4; typed against the contract types so drift fails type-checking. */

export const ALLOWED_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["RESOLVED", "CANCELLED"],
  RESOLVED: ["CLOSED"],
  CLOSED: [],
  CANCELLED: [],
};

export function aTicket(overrides: Partial<TicketResponse> = {}): TicketResponse {
  const status = overrides.status ?? "OPEN";
  return {
    id: 42,
    title: "Cannot log in to portal",
    description: "User reports a 403 after password reset.",
    priority: "HIGH",
    status,
    assignee: null,
    createdAt: "2026-09-25T09:00:00Z",
    updatedAt: "2026-09-25T09:00:00Z",
    resolvedAt: null,
    closedAt: null,
    cancelledAt: null,
    version: 2,
    allowedTransitions: ALLOWED_TRANSITIONS[status],
    ...overrides,
  };
}

export function aSummary(overrides: Partial<TicketSummaryResponse> = {}): TicketSummaryResponse {
  return {
    id: 42,
    title: "Cannot log in to portal",
    priority: "HIGH",
    status: "OPEN",
    assignee: null,
    createdAt: "2026-09-25T09:00:00Z",
    updatedAt: "2026-09-25T09:00:00Z",
    ...overrides,
  };
}

export function aComment(overrides: Partial<CommentResponse> = {}): CommentResponse {
  return {
    id: 1,
    ticketId: 42,
    author: "maria.lopez",
    body: "Looking into it",
    createdAt: "2026-09-25T10:00:00Z",
    ...overrides,
  };
}

export function aPage<T>(content: T[], page = { number: 0, size: 20 }): PageResponse<T> {
  const totalElements = content.length;
  return {
    content,
    page: { ...page, totalElements, totalPages: totalElements === 0 ? 0 : Math.ceil(totalElements / page.size) },
  };
}

/** A Problem Details response exactly as api-contract §2 defines it. */
export function problem(
  status: number,
  code: string,
  extras: { detail?: string; errors?: ProblemFieldError[]; [extension: string]: unknown } = {},
) {
  const { detail = `Detail for ${code}`, errors = [], ...extensions } = extras;
  const body: Problem = {
    type: `https://supportdesk.example/problems/${code.toLowerCase().replaceAll("_", "-")}`,
    title: code,
    status,
    detail,
    instance: "/api/v1/tickets",
    code,
    correlationId: "corr-123",
    timestamp: "2026-09-25T10:00:00Z",
    errors,
    ...extensions,
  };
  return HttpResponse.json(body, {
    status,
    headers: { "Content-Type": "application/problem+json", "X-Correlation-Id": "corr-123" },
  });
}

export function fieldError(field: string | null, code: string): ProblemFieldError {
  return { location: "body", field, code, message: `server message for ${field}:${code}` };
}
