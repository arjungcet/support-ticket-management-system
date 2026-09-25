import { describe, expect, it } from "vitest";
import { ticketListQuery } from "@/features/tickets/api";
import { parseTicketListParams } from "@/features/tickets/hooks/useTicketListParams";

describe("ticketListQuery (api-contract §6.2)", () => {
  it("sends trimmed q, repeated status, page, size and sort", () => {
    const query = ticketListQuery({
      q: "  log in  ",
      status: ["OPEN", "IN_PROGRESS"],
      page: 2,
      size: 20,
      sort: "priority,desc",
    });

    const params = new URLSearchParams(query);
    expect(params.get("q")).toBe("log in");
    expect(params.getAll("status")).toEqual(["OPEN", "IN_PROGRESS"]);
    expect(params.get("page")).toBe("2");
    expect(params.get("size")).toBe("20");
    expect(params.get("sort")).toBe("priority,desc");
  });

  it("omits an empty or whitespace keyword and an empty status filter", () => {
    const params = new URLSearchParams(
      ticketListQuery({ q: "   ", status: [], page: 0, size: 20, sort: "createdAt,desc" }),
    );

    expect(params.has("q")).toBe(false);
    expect(params.has("status")).toBe(false);
  });

  it("encodes special characters so they reach the server literally", () => {
    const params = new URLSearchParams(
      ticketListQuery({ q: "100% & code_2026", status: [], page: 0, size: 20, sort: "createdAt,desc" }),
    );

    expect(params.get("q")).toBe("100% & code_2026");
  });
});

describe("parseTicketListParams (URL state, TS-FE-04)", () => {
  it("round-trips through the URL", () => {
    const original = { q: "printer", status: ["RESOLVED" as const], page: 3, size: 20, sort: "status,asc" };

    expect(parseTicketListParams(new URLSearchParams(ticketListQuery(original)))).toEqual(original);
  });

  it("accepts comma-separated statuses, drops unknown ones and duplicates, and defaults the rest", () => {
    const parsed = parseTicketListParams(new URLSearchParams("status=OPEN,FOO&status=OPEN&page=-4"));

    expect(parsed).toEqual({ q: "", status: ["OPEN"], page: 0, size: 20, sort: "createdAt,desc" });
  });
});
