/**
 * API types transcribed from spec/api-contract.md (§3 enumerations, §4 representations, §6 request bodies, §2 errors).
 *
 * The implementation plan (STEP-47) generates these from spec/openapi.yaml; that file does not exist yet, so they are
 * maintained by hand for now. Do not add fields or endpoints that are not in the contract.
 */

export const TICKET_STATUSES = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"] as const;
export type TicketStatus = (typeof TICKET_STATUSES)[number];

export const TICKET_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"] as const;
export type TicketPriority = (typeof TICKET_PRIORITIES)[number];

export const TICKET_SORT_FIELDS = ["createdAt", "updatedAt", "priority", "status"] as const;
export type TicketSortField = (typeof TICKET_SORT_FIELDS)[number];
export type SortDirection = "asc" | "desc";

/** api-contract §4.1 */
export interface TicketResponse {
  id: number;
  title: string;
  description: string;
  priority: TicketPriority;
  status: TicketStatus;
  assignee: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  cancelledAt: string | null;
  version: number;
  allowedTransitions: TicketStatus[];
}

/** api-contract §4.2 */
export interface TicketSummaryResponse {
  id: number;
  title: string;
  priority: TicketPriority;
  status: TicketStatus;
  assignee: string | null;
  createdAt: string;
  updatedAt: string;
}

/** api-contract §4.3 */
export interface CommentResponse {
  id: number;
  ticketId: number;
  author: string;
  body: string;
  createdAt: string;
}

/** api-contract §4.4 */
export interface PageResponse<T> {
  content: T[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}

/** api-contract §6.1 */
export interface CreateTicketRequest {
  title: string;
  description: string;
  priority?: TicketPriority;
  assignee?: string | null;
}

/** api-contract §6.4 — at least one of title/description/priority besides version. */
export interface UpdateTicketRequest {
  version: number;
  title?: string;
  description?: string;
  priority?: TicketPriority;
}

/** api-contract §6.5 — null unassigns. */
export interface AssignTicketRequest {
  version: number;
  assignee: string | null;
}

/** api-contract §6.9 */
export interface ChangeStatusRequest {
  version: number;
  targetStatus: TicketStatus;
}

/** api-contract §6.6 */
export interface AddCommentRequest {
  author: string;
  body: string;
}

/** api-contract §2 */
export interface ProblemFieldError {
  location: "body" | "query" | "path";
  field: string | null;
  code: string;
  message: string;
}

/** api-contract §2 — RFC 9457 Problem Details with project extensions. */
export interface Problem {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  correlationId: string;
  timestamp: string;
  errors: ProblemFieldError[];
  [extension: string]: unknown;
}

/** Field limits after trimming — spec/api-contract.md §2.3 (source: spec/data-model.md §7). */
export const FIELD_LIMITS = {
  title: 200,
  description: 5000,
  assignee: 100,
  author: 100,
  body: 5000,
  q: 100,
} as const;

export const DEFAULT_PAGE_SIZE = 20;
export const DEFAULT_COMMENT_PAGE_SIZE = 50;
