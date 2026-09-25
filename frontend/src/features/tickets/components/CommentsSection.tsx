"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { Field } from "@/components/ui/Field";
import { isApiError } from "@/lib/api/errors";
import { applyServerFieldErrors } from "@/lib/api/formErrors";
import { FIELD_LIMITS, type TicketResponse } from "@/lib/api/types";
import { useAddComment, useComments, useReloadTicket } from "../hooks/queries";
import { TERMINAL_STATUSES, formatTimestamp } from "../labels";
import { textRules } from "./validation";

const AUTHOR_STORAGE_KEY = "supportdesk.commentAuthor";
const FIELDS = ["author", "body"] as const;

function readRememberedAuthor(): string {
  try {
    return window.localStorage.getItem(AUTHOR_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

function rememberAuthor(author: string) {
  try {
    window.localStorage.setItem(AUTHOR_STORAGE_KEY, author);
  } catch {
    // Storage unavailable (private mode etc.) — remembering the name is only a convenience.
  }
}

interface CommentValues {
  author: string;
  body: string;
}

/** REQ-5: comments oldest first, paged; add form hidden for terminal tickets (A-18). Author is free text (A-14). */
export function CommentsSection({ ticket }: { ticket: TicketResponse }) {
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = useComments(ticket.id, page);
  const addComment = useAddComment(ticket.id);
  const reloadTicket = useReloadTicket(ticket.id);
  const [failure, setFailure] = useState<{ error: unknown; details: string[] } | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm<CommentValues>({ defaultValues: { author: readRememberedAuthor(), body: "" } });

  const onSubmit = handleSubmit(async (values) => {
    setFailure(null);
    try {
      await addComment.mutateAsync(values);
      rememberAuthor(values.author.trim());
      resetField("body");
    } catch (submitError) {
      setFailure({ error: submitError, details: applyServerFieldErrors(submitError, setError, FIELDS) });
      if (isApiError(submitError) && submitError.code === "TICKET_NOT_COMMENTABLE") {
        void reloadTicket();
      }
    }
  });

  const totalPages = data?.page.totalPages ?? 0;
  const commentable = !TERMINAL_STATUSES.has(ticket.status);

  return (
    <section aria-label="Comments">
      <h2>Comments</h2>
      {error && <ErrorAlert error={error} onRetry={() => void refetch()} />}
      {isPending && !error && <p>Loading comments…</p>}
      {data && data.content.length === 0 && <p>No comments yet.</p>}
      {data && data.content.length > 0 && (
        <ol className="comments">
          {data.content.map((comment) => (
            <li key={comment.id}>
              <p className="muted">
                {comment.author} · {formatTimestamp(comment.createdAt)}
              </p>
              <p className="pre-wrap">{comment.body}</p>
            </li>
          ))}
        </ol>
      )}
      {totalPages > 1 && (
        <nav aria-label="Comment pages" className="pagination">
          <button type="button" disabled={page <= 0} onClick={() => setPage(page - 1)}>
            Older
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>
            Newer
          </button>
        </nav>
      )}

      {failure && <ErrorAlert error={failure.error} details={failure.details} />}
      {commentable ? (
        <form onSubmit={onSubmit} noValidate aria-label="Add comment">
          <Field id="comment-author" label="Your name" error={errors.author?.message}>
            <input {...register("author", textRules(FIELD_LIMITS.author, true))} />
          </Field>
          <Field id="comment-body" label="Comment" error={errors.body?.message}>
            <textarea rows={4} {...register("body", textRules(FIELD_LIMITS.body, true))} />
          </Field>
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Adding…" : "Add comment"}
          </button>
        </form>
      ) : (
        <p className="muted">Closed or cancelled tickets can&apos;t receive new comments.</p>
      )}
    </section>
  );
}
