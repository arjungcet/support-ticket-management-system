"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorAlert } from "@/components/ui/ErrorAlert";
import { FIELD_LIMITS, TICKET_STATUSES, type TicketStatus } from "@/lib/api/types";
import type { TicketListParams } from "../api";
import { useTickets } from "../hooks/queries";
import { STATUS_LABELS, formatTimestamp } from "../labels";
import { PriorityBadge, StatusBadge } from "./Badges";

const SORT_OPTIONS = [
  { value: "createdAt,desc", label: "Newest first" },
  { value: "createdAt,asc", label: "Oldest first" },
  { value: "updatedAt,desc", label: "Last modified" },
  { value: "priority,desc", label: "Priority: high to low" },
  { value: "priority,asc", label: "Priority: low to high" },
  { value: "status,asc", label: "Status" },
];

interface TicketListViewProps {
  params: TicketListParams;
  onParamsChange: (params: TicketListParams) => void;
  searchDebounceMs?: number;
}

/** REQ-2/6/7: list with keyword search, status filter, sorting and paging. State lives in `params` (the URL). */
export function TicketListView({ params, onParamsChange, searchDebounceMs = 300 }: TicketListViewProps) {
  const { data, error, isPending, isFetching, refetch } = useTickets(params);
  const [keyword, setKeyword] = useState(params.q);
  const [previousQ, setPreviousQ] = useState(params.q);
  // Keywords this component pushed recently. URL updates are asynchronous and may arrive after newer pushes.
  const [pushedKeywords, setPushedKeywords] = useState<string[]>([]);

  // When the URL's keyword changes to something this component did not push (Back button, nav link), show it
  // instead of re-applying the old keyword. Updates caused by our own debounced pushes keep what the user is typing.
  if (params.q !== previousQ) {
    setPreviousQ(params.q);
    if (!pushedKeywords.includes(params.q.trim())) {
      setKeyword(params.q);
      setPushedKeywords([]);
    }
  }

  useEffect(() => {
    const trimmed = keyword.trim();
    if (trimmed === params.q.trim()) {
      return;
    }
    const timer = setTimeout(() => {
      setPushedKeywords((pushed) => [...pushed.slice(-9), trimmed]);
      onParamsChange({ ...params, q: keyword, page: 0 });
    }, searchDebounceMs);
    return () => clearTimeout(timer);
  }, [keyword, params, onParamsChange, searchDebounceMs]);

  const toggleStatus = (status: TicketStatus) => {
    const selected = params.status.includes(status)
      ? params.status.filter((s) => s !== status)
      : [...params.status, status];
    onParamsChange({ ...params, status: selected, page: 0 });
  };

  const totalPages = data?.page.totalPages ?? 0;

  return (
    <section aria-label="Tickets">
      <div className="toolbar">
        <div className="control">
          <label htmlFor="ticket-search">Search</label>
          <input
            id="ticket-search"
            type="search"
            value={keyword}
            maxLength={FIELD_LIMITS.q}
            placeholder="Search title or description"
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
        <fieldset className="chips">
          <legend>Status</legend>
          {TICKET_STATUSES.map((status) => (
            <label key={status} className="chip">
              <input
                type="checkbox"
                checked={params.status.includes(status)}
                onChange={() => toggleStatus(status)}
              />
              {STATUS_LABELS[status]}
            </label>
          ))}
        </fieldset>
        {/* Label and select stay together so the row can't wrap between them. */}
        <div className="control">
          <label htmlFor="ticket-sort">Sort</label>
          <select
            id="ticket-sort"
            value={params.sort}
            onChange={(event) => onParamsChange({ ...params, sort: event.target.value, page: 0 })}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <ErrorAlert error={error} onRetry={() => void refetch()} />}
      {isPending && !error && <p>Loading tickets…</p>}
      {data && data.content.length === 0 && <p className="empty">No tickets match.</p>}
      {data && data.content.length > 0 && (
        <div className="table-card">
          <table aria-busy={isFetching}>
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Title</th>
                <th scope="col">Status</th>
                <th scope="col">Priority</th>
                <th scope="col">Assignee</th>
                <th scope="col">Last modified</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((ticket) => (
                <tr key={ticket.id}>
                  <td className="muted">#{ticket.id}</td>
                  <td>
                    <Link className="ticket-link" href={`/tickets/${ticket.id}`}>
                      {ticket.title}
                    </Link>
                  </td>
                  <td>
                    <StatusBadge status={ticket.status} />
                  </td>
                  <td>
                    <PriorityBadge priority={ticket.priority} />
                  </td>
                  <td className={ticket.assignee ? undefined : "muted"}>{ticket.assignee ?? "Unassigned"}</td>
                  <td className="muted">{formatTimestamp(ticket.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && totalPages > 0 && (
        <nav aria-label="Pagination" className="pagination">
          <button
            type="button"
            disabled={params.page <= 0}
            onClick={() => onParamsChange({ ...params, page: params.page - 1 })}
          >
            Previous
          </button>
          <span>
            Page {params.page + 1} of {totalPages} ({data.page.totalElements} tickets)
          </span>
          <button
            type="button"
            disabled={params.page + 1 >= totalPages}
            onClick={() => onParamsChange({ ...params, page: params.page + 1 })}
          >
            Next
          </button>
        </nav>
      )}
    </section>
  );
}
