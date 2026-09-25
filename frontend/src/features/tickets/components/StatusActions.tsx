"use client";

import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { isApiError } from "@/lib/api/errors";
import type { TicketResponse, TicketStatus } from "@/lib/api/types";
import { useChangeStatus, useReloadTicket } from "../hooks/queries";
import { useActionFailure } from "../hooks/useActionFailure";
import { TRANSITION_LABELS } from "../labels";

/**
 * REQ-11/12: one button per entry in the server's `allowedTransitions` — the UI keeps no transition table of its own
 * (spec/state-machine.md §5 E7). A rejected transition shows the server's reason and re-reads the ticket.
 */
export function StatusActions({ ticket }: { ticket: TicketResponse }) {
  const changeStatus = useChangeStatus(ticket.id);
  const reloadTicket = useReloadTicket(ticket.id);
  const [failure, setFailure] = useActionFailure<unknown>(ticket.version, (error) => error);

  const move = async (targetStatus: TicketStatus) => {
    setFailure(null);
    try {
      await changeStatus.mutateAsync({ version: ticket.version, targetStatus });
    } catch (error) {
      setFailure(error);
      if (isApiError(error) && error.code === "TICKET_INVALID_TRANSITION") {
        void reloadTicket();
      }
    }
  };

  const isConflict = isApiError(failure) && failure.code === "TICKET_CONCURRENT_MODIFICATION";

  return (
    <section aria-label="Status actions">
      {failure !== null && (
        <ErrorAlert error={failure} onReload={isConflict ? () => void reloadTicket() : undefined} />
      )}
      {ticket.allowedTransitions.length === 0 ? (
        <p className="muted">No further status changes are possible.</p>
      ) : (
        ticket.allowedTransitions.map((target) => (
          <button
            key={target}
            type="button"
            className={target === "CANCELLED" ? "danger" : "primary"}
            disabled={changeStatus.isPending}
            onClick={() => void move(target)}
          >
            {TRANSITION_LABELS[target]}
          </button>
        ))
      )}
    </section>
  );
}
