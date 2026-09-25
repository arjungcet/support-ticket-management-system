"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { Field } from "@/components/ui/Field";
import { applyServerFieldErrors } from "@/lib/api/formErrors";
import { FIELD_LIMITS, TICKET_PRIORITIES, type TicketPriority, type TicketResponse } from "@/lib/api/types";
import { useCreateTicket } from "../hooks/queries";
import { PRIORITY_LABELS } from "../labels";
import { textRules } from "./validation";

interface CreateValues {
  title: string;
  description: string;
  priority: TicketPriority;
  assignee: string;
}

const FIELDS = ["title", "description", "priority", "assignee"] as const;

export function CreateTicketForm({ onCreated }: { onCreated: (ticket: TicketResponse) => void }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateValues>({ defaultValues: { title: "", description: "", priority: "MEDIUM", assignee: "" } });
  const createTicket = useCreateTicket();
  const [failure, setFailure] = useState<{ error: unknown; details: string[] } | null>(null);

  const onSubmit = handleSubmit(async (values) => {
    setFailure(null);
    try {
      const ticket = await createTicket.mutateAsync({
        title: values.title,
        description: values.description,
        priority: values.priority,
        assignee: values.assignee.trim() ? values.assignee : null,
      });
      onCreated(ticket);
    } catch (error) {
      setFailure({ error, details: applyServerFieldErrors(error, setError, FIELDS) });
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate aria-label="Create ticket">
      {failure && <ErrorAlert error={failure.error} details={failure.details} />}
      <Field id="title" label="Title" error={errors.title?.message}>
        <input {...register("title", textRules(FIELD_LIMITS.title, true))} />
      </Field>
      <Field id="description" label="Description" error={errors.description?.message}>
        <textarea rows={6} {...register("description", textRules(FIELD_LIMITS.description, true))} />
      </Field>
      <Field id="priority" label="Priority" error={errors.priority?.message}>
        <select {...register("priority")}>
          {TICKET_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {PRIORITY_LABELS[priority]}
            </option>
          ))}
        </select>
      </Field>
      <Field id="assignee" label="Assignee (optional)" error={errors.assignee?.message}>
        <input {...register("assignee", textRules(FIELD_LIMITS.assignee, false))} />
      </Field>
      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Creating…" : "Create ticket"}
      </button>
    </form>
  );
}
