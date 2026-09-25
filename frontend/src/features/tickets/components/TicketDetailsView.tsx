"use client";

import Link from "next/link";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { isApiError } from "@/lib/api/errors";
import { useTicket } from "../hooks/queries";
import { PRIORITY_LABELS, STATUS_LABELS, TERMINAL_STATUSES, formatTimestamp } from "../labels";
import { AssigneeControl } from "./AssigneeControl";
import { CommentsSection } from "./CommentsSection";
import { EditTicketForm } from "./EditTicketForm";
import { StatusActions } from "./StatusActions";

export function TicketNotFound() {
  return (
    <section>
      <h1>Ticket not found</h1>
      <p>This ticket does not exist or has been removed.</p>
      <Link href="/tickets">Back to tickets</Link>
    </section>
  );
}

/** REQ-3: ticket details with edit, assignee, status and comment sections. */
export function TicketDetailsView({ ticketId }: { ticketId: number }) {
  const { data: ticket, error, isPending, refetch } = useTicket(ticketId);

  if (isApiError(error) && error.code === "TICKET_NOT_FOUND") {
    return <TicketNotFound />;
  }
  if (error && !ticket) {
    return <ErrorAlert error={error} onRetry={() => void refetch()} />;
  }
  if (isPending || !ticket) {
    return <p>Loading ticket…</p>;
  }

  const editable = !TERMINAL_STATUSES.has(ticket.status);

  return (
    <article>
      <p>
        <Link href="/tickets">← All tickets</Link>
      </p>
      <h1>
        #{ticket.id} {ticket.title}
      </h1>
      <dl className="details">
        <dt>Status</dt>
        <dd>{STATUS_LABELS[ticket.status]}</dd>
        <dt>Priority</dt>
        <dd>{PRIORITY_LABELS[ticket.priority]}</dd>
        <dt>Assignee</dt>
        <dd>{ticket.assignee ?? "Unassigned"}</dd>
        <dt>Created</dt>
        <dd>{formatTimestamp(ticket.createdAt)}</dd>
        <dt>Last modified</dt>
        <dd>{formatTimestamp(ticket.updatedAt)}</dd>
        {ticket.resolvedAt && (
          <>
            <dt>Resolved</dt>
            <dd>{formatTimestamp(ticket.resolvedAt)}</dd>
          </>
        )}
        {ticket.closedAt && (
          <>
            <dt>Closed</dt>
            <dd>{formatTimestamp(ticket.closedAt)}</dd>
          </>
        )}
        {ticket.cancelledAt && (
          <>
            <dt>Cancelled</dt>
            <dd>{formatTimestamp(ticket.cancelledAt)}</dd>
          </>
        )}
      </dl>
      <h2>Description</h2>
      <p className="pre-wrap">{ticket.description}</p>

      <h2>Status</h2>
      <StatusActions ticket={ticket} />

      {editable && (
        <>
          <h2>Assignee</h2>
          <AssigneeControl key={`assignee-${ticket.assignee ?? ""}`} ticket={ticket} />
          <h2>Edit ticket</h2>
          <EditTicketForm ticket={ticket} />
        </>
      )}

      <CommentsSection ticket={ticket} />
    </article>
  );
}
