"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo } from "react";
import { DEFAULT_PAGE_SIZE, TICKET_STATUSES, type TicketStatus } from "@/lib/api/types";
import { ticketListQuery, type TicketListParams } from "../api";

const DEFAULT_SORT = "createdAt,desc";

/** Parses list state from the URL (`?q=&status=&page=&sort=`), accepting repeated or comma-separated statuses. */
export function parseTicketListParams(search: URLSearchParams): TicketListParams {
  const statuses = search
    .getAll("status")
    .flatMap((value) => value.split(","))
    .filter((value): value is TicketStatus => (TICKET_STATUSES as readonly string[]).includes(value));
  const page = Number.parseInt(search.get("page") ?? "0", 10);
  return {
    q: search.get("q") ?? "",
    status: [...new Set(statuses)],
    page: Number.isFinite(page) && page > 0 ? page : 0,
    size: DEFAULT_PAGE_SIZE,
    sort: search.get("sort") ?? DEFAULT_SORT,
  };
}

/** List state lives in the URL so it survives reloads and can be shared (spec/architecture.md §6.2). */
export function useTicketListParams(): [TicketListParams, (params: TicketListParams) => void] {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const params = useMemo(() => parseTicketListParams(new URLSearchParams(searchParams.toString())), [searchParams]);
  const setParams = useCallback(
    (next: TicketListParams) => router.replace(`${pathname}?${ticketListQuery(next)}`, { scroll: false }),
    [router, pathname],
  );
  return [params, setParams];
}
