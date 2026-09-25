import type { TicketPriority, TicketStatus } from "@/lib/api/types";

/** Display labels only. Which transitions are allowed always comes from the server (allowedTransitions). */
export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

/** Button label for moving a ticket to the given status. */
export const TRANSITION_LABELS: Record<TicketStatus, string> = {
  OPEN: "Reopen",
  IN_PROGRESS: "Start progress",
  RESOLVED: "Resolve",
  CLOSED: "Close",
  CANCELLED: "Cancel ticket",
};

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

/**
 * Terminal states: edit, assign and comment controls are hidden (spec/state-machine.md §1, A-17/A-18). This only
 * shapes the UI — the backend still rejects such requests with 422 whatever the client sends.
 */
export const TERMINAL_STATUSES: ReadonlySet<TicketStatus> = new Set(["CLOSED", "CANCELLED"]);

export function formatTimestamp(value: string | null): string {
  return value ? new Date(value).toLocaleString() : "—";
}
