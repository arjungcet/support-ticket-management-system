import type { TicketPriority, TicketStatus } from "@/lib/api/types";
import { PRIORITY_LABELS, STATUS_LABELS } from "../labels";

/** Visual only: the badge text is the plain label, so screen readers and text queries see the same words. */
export function StatusBadge({ status }: { status: TicketStatus }) {
  return <span className={`badge status-${status.toLowerCase().replace("_", "-")}`}>{STATUS_LABELS[status]}</span>;
}

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  return <span className={`badge priority-${priority.toLowerCase()}`}>{PRIORITY_LABELS[priority]}</span>;
}
