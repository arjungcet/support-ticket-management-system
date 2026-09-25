"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { Field } from "@/components/ui/Field";
import { isApiError } from "@/lib/api/errors";
import { applyServerFieldErrors } from "@/lib/api/formErrors";
import {
  FIELD_LIMITS,
  TICKET_PRIORITIES,
  type TicketPriority,
  type TicketResponse,
  type UpdateTicketRequest,
} from "@/lib/api/types";
import { useReloadTicket, useUpdateTicket } from "../hooks/queries";
import { useActionFailure } from "../hooks/useActionFailure";
import { PRIORITY_LABELS } from "../labels";
import { textRules } from "./validation";

interface EditValues {
  title: string;
  description: string;
  priority: TicketPriority;
}

const FIELDS = ["title", "description", "priority"] as const;

function valuesOf(ticket: TicketResponse): EditValues {
  return { title: ticket.title, description: ticket.description, priority: ticket.priority };
}

/**
 * REQ-4 (title, description, priority): PATCH with only the fields the user edited plus the ticket's current
 * `version`. On a version conflict the user's input is kept so they can reload and save again; fields the user did
 * not touch follow the reloaded ticket, so saving never sends back stale values (no lost update).
 */
export function EditTicketForm({ ticket }: { ticket: TicketResponse }) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isDirty, dirtyFields, isSubmitting },
  } = useForm<EditValues>({ defaultValues: valuesOf(ticket) });

  // When the ticket changes underneath the form (reload, refetch), untouched fields take the new values and the
  // user's edits are kept.
  useEffect(() => {
    reset(valuesOf(ticket), { keepDirtyValues: true });
  }, [ticket, reset]);
  const updateTicket = useUpdateTicket(ticket.id);
  const reloadTicket = useReloadTicket(ticket.id);
  const [failure, setFailure] = useActionFailure<{ error: unknown; details: string[] }>(
    ticket.version,
    (current) => current.error,
  );

  const onSubmit = handleSubmit(async (values) => {
    setFailure(null);
    const body: UpdateTicketRequest = { version: ticket.version };
    if (dirtyFields.title && values.title !== ticket.title) body.title = values.title;
    if (dirtyFields.description && values.description !== ticket.description) body.description = values.description;
    if (dirtyFields.priority && values.priority !== ticket.priority) body.priority = values.priority;
    if (Object.keys(body).length === 1) {
      // The user's edits already match the current ticket: nothing to send.
      reset(valuesOf(ticket));
      return;
    }
    try {
      const saved = await updateTicket.mutateAsync(body);
      reset(valuesOf(saved));
    } catch (error) {
      setFailure({ error, details: applyServerFieldErrors(error, setError, FIELDS) });
    }
  });

  const isConflict = isApiError(failure?.error) && failure.error.code === "TICKET_CONCURRENT_MODIFICATION";

  return (
    <form onSubmit={onSubmit} noValidate aria-label="Edit ticket">
      {failure && (
        <ErrorAlert
          error={failure.error}
          details={failure.details}
          onReload={isConflict ? () => void reloadTicket() : undefined}
        />
      )}
      <Field id="edit-title" label="Title" error={errors.title?.message}>
        <input {...register("title", textRules(FIELD_LIMITS.title, true))} />
      </Field>
      <Field id="edit-description" label="Description" error={errors.description?.message}>
        <textarea rows={6} {...register("description", textRules(FIELD_LIMITS.description, true, true))} />
      </Field>
      <Field id="edit-priority" label="Priority" error={errors.priority?.message}>
        <select {...register("priority")}>
          {TICKET_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {PRIORITY_LABELS[priority]}
            </option>
          ))}
        </select>
      </Field>
      <button type="submit" disabled={!isDirty || isSubmitting}>
        {isSubmitting ? "Saving…" : "Save changes"}
      </button>
    </form>
  );
}
