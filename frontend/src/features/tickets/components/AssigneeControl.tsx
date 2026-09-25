"use client";

import { useState } from "react";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { Field } from "@/components/ui/Field";
import { fieldMessage, isApiError } from "@/lib/api/errors";
import { FIELD_LIMITS, type TicketResponse } from "@/lib/api/types";
import { useAssignTicket, useReloadTicket } from "../hooks/queries";
import { useActionFailure } from "../hooks/useActionFailure";

/**
 * REQ-4 (assignee): a separate, immediately-saved control using PUT /tickets/{id}/assignee, so it never shares a
 * save with the details form (review SR-13). `null` unassigns.
 */
export function AssigneeControl({ ticket }: { ticket: TicketResponse }) {
  const [assignee, setAssignee] = useState(ticket.assignee ?? "");
  const assignTicket = useAssignTicket(ticket.id);
  const reloadTicket = useReloadTicket(ticket.id);
  const [failure, setFailure] = useActionFailure<unknown>(ticket.version, (error) => error);

  const save = async (value: string | null) => {
    setFailure(null);
    try {
      const saved = await assignTicket.mutateAsync({ version: ticket.version, assignee: value });
      setAssignee(saved.assignee ?? "");
    } catch (error) {
      setFailure(error);
    }
  };

  const tooLong = assignee.trim().length > FIELD_LIMITS.assignee;
  const unchanged = assignee.trim() === (ticket.assignee ?? "");
  const fieldError = isApiError(failure)
    ? failure.fieldErrors.find((error) => error.field === "assignee")
    : undefined;
  const isConflict = isApiError(failure) && failure.code === "TICKET_CONCURRENT_MODIFICATION";

  return (
    <form
      aria-label="Assignee"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void save(assignee.trim() ? assignee : null);
      }}
    >
      {failure !== null && (
        <ErrorAlert error={failure} onReload={isConflict ? () => void reloadTicket() : undefined} />
      )}
      <Field
        id="assignee-input"
        label="Assignee"
        error={tooLong ? `Must be at most ${FIELD_LIMITS.assignee} characters.` : fieldError && fieldMessage(fieldError)}
      >
        <input value={assignee} onChange={(event) => setAssignee(event.target.value)} />
      </Field>
      <button type="submit" disabled={unchanged || tooLong || assignTicket.isPending}>
        Save assignee
      </button>
      {ticket.assignee && (
        <button type="button" disabled={assignTicket.isPending} onClick={() => void save(null)}>
          Unassign
        </button>
      )}
    </form>
  );
}
