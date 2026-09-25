import { describe, expect, it } from "vitest";
import { ApiError, ERROR_MESSAGES, fieldMessage, userMessage } from "@/lib/api/errors";
import type { Problem } from "@/lib/api/types";

/** Every error code in spec/api-contract.md §2.1. */
const CONTRACT_CODES = [
  "MALFORMED_REQUEST",
  "VALIDATION_FAILED",
  "TICKET_NOT_FOUND",
  "RESOURCE_NOT_FOUND",
  "METHOD_NOT_ALLOWED",
  "TICKET_CONCURRENT_MODIFICATION",
  "TICKET_INVALID_TRANSITION",
  "UNSUPPORTED_MEDIA_TYPE",
  "TICKET_NOT_EDITABLE",
  "TICKET_NOT_COMMENTABLE",
  "INTERNAL_ERROR",
];

function problemFor(code: string, detail: string): Problem {
  return {
    type: "t",
    title: "t",
    status: 409,
    detail,
    instance: "/api/v1/tickets/1",
    code,
    correlationId: "c",
    timestamp: "2026-09-25T10:00:00Z",
    errors: [],
  };
}

describe("error message catalogue (REQ-10)", () => {
  it.each(CONTRACT_CODES)("has a user message for %s", (code) => {
    expect(ERROR_MESSAGES[code]).toBeTruthy();
  });

  it("shows the server's detail for invalid transitions", () => {
    const error = new ApiError(409, "TICKET_INVALID_TRANSITION", problemFor(
      "TICKET_INVALID_TRANSITION", "Ticket 42 cannot move from CLOSED to OPEN."), "c");

    expect(userMessage(error)).toContain("Ticket 42 cannot move from CLOSED to OPEN.");
  });

  it("does not show detail text for other codes (the UI maps on code, not text)", () => {
    const error = new ApiError(422, "TICKET_NOT_EDITABLE", problemFor("TICKET_NOT_EDITABLE", "internal wording"), "c");

    expect(userMessage(error)).toBe(ERROR_MESSAGES.TICKET_NOT_EDITABLE);
  });

  it("falls back to a generic message for unknown codes and non-API errors", () => {
    expect(userMessage(new ApiError(418, "SOMETHING_NEW", null, null))).toBe(ERROR_MESSAGES.UNEXPECTED_RESPONSE);
    expect(userMessage(new TypeError("boom"))).toBe(ERROR_MESSAGES.INTERNAL_ERROR);
  });
});

describe("fieldMessage", () => {
  it.each([
    ["REQUIRED", "title", "This field is required."],
    ["BLANK", "title", "This field can't be blank."],
    ["TOO_LONG", "title", "Must be at most 200 characters."],
    ["TOO_LONG", "description", "Must be at most 5000 characters."],
    ["INVALID_VALUE", "priority", "This value is not valid."],
    ["NO_CHANGES_REQUESTED", null, "Change at least one field before saving."],
  ])("%s on %s", (code, field, expected) => {
    expect(fieldMessage({ location: "body", field, code, message: "server text" })).toBe(expected);
  });
});
