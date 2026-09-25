import { FIELD_LIMITS, type Problem, type ProblemFieldError } from "./types";

/**
 * User-facing message per error `code` (spec/api-contract.md §2.1). The UI branches on `code`, never on `detail`
 * text. NETWORK_ERROR and UNEXPECTED_RESPONSE are client-side codes for failures that produce no Problem Details.
 */
export const ERROR_MESSAGES: Record<string, string> = {
  MALFORMED_REQUEST: "The request could not be understood. Please try again.",
  VALIDATION_FAILED: "Please correct the highlighted fields.",
  TICKET_NOT_FOUND: "This ticket does not exist or has been removed.",
  RESOURCE_NOT_FOUND: "The requested resource was not found.",
  METHOD_NOT_ALLOWED: "This action is not supported.",
  TICKET_CONCURRENT_MODIFICATION: "This ticket was changed by someone else. Reload to see the latest version.",
  TICKET_INVALID_TRANSITION: "That status change is not allowed from the ticket's current status.",
  UNSUPPORTED_MEDIA_TYPE: "The request format is not supported.",
  TICKET_NOT_EDITABLE: "Closed or cancelled tickets can't be edited.",
  TICKET_NOT_COMMENTABLE: "Closed or cancelled tickets can't receive new comments.",
  INTERNAL_ERROR: "Something went wrong on our side. Please try again.",
  NETWORK_ERROR: "Can't reach the server. Check your connection and try again.",
  UNEXPECTED_RESPONSE: "The server returned an unexpected response. Please try again.",
};

/** Codes whose `detail` is specific and safe to show in addition to the generic message (api-contract §2). */
const SHOW_DETAIL = new Set(["TICKET_INVALID_TRANSITION"]);

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: Problem | null;
  readonly correlationId: string | null;

  constructor(status: number, code: string, problem: Problem | null, correlationId: string | null) {
    super(problem?.detail ?? code);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.problem = problem;
    this.correlationId = correlationId;
  }

  static network(): ApiError {
    return new ApiError(0, "NETWORK_ERROR", null, null);
  }

  get fieldErrors(): ProblemFieldError[] {
    return this.problem?.errors ?? [];
  }

  /** 5xx and transport failures are worth retrying and should show a support reference. */
  get isServerOrNetwork(): boolean {
    return this.status === 0 || this.status >= 500;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** The message to show for an error, suitable for an alert. */
export function userMessage(error: unknown): string {
  if (!isApiError(error)) {
    return ERROR_MESSAGES.INTERNAL_ERROR;
  }
  const base = ERROR_MESSAGES[error.code] ?? ERROR_MESSAGES.UNEXPECTED_RESPONSE;
  if (SHOW_DETAIL.has(error.code) && error.problem?.detail) {
    return `${base} ${error.problem.detail}`;
  }
  return base;
}

/** Message for one entry of `errors[]`, placed next to its field. */
export function fieldMessage(error: ProblemFieldError): string {
  const limit = error.field ? FIELD_LIMITS[error.field as keyof typeof FIELD_LIMITS] : undefined;
  switch (error.code) {
    case "REQUIRED":
      return "This field is required.";
    case "BLANK":
      return "This field can't be blank.";
    case "TOO_LONG":
      return limit ? `Must be at most ${limit} characters.` : "This value is too long.";
    case "INVALID_VALUE":
      return "This value is not valid.";
    case "NO_CHANGES_REQUESTED":
      return "Change at least one field before saving.";
    default:
      return error.message || "This value is not valid.";
  }
}
