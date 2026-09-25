import { screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { aPage, aSummary, problem } from "@/test/fixtures";
import { renderWithClient } from "@/test/render";
import { server } from "@/test/server";
import type { TicketListParams } from "../api";
import { TicketListView } from "./TicketListView";

const DEFAULTS: TicketListParams = { q: "", status: [], page: 0, size: 20, sort: "createdAt,desc" };

function captureListRequests() {
  const requests: URLSearchParams[] = [];
  return {
    requests,
    handler: (page: ReturnType<typeof aPage>) =>
      http.get("/api/v1/tickets", ({ request }) => {
        requests.push(new URL(request.url).searchParams);
        return HttpResponse.json(page);
      }),
  };
}

describe("TicketListView (REQ-2, TS-FE-03/05)", () => {
  it("renders ticket summaries with links to details", async () => {
    server.use(
      http.get("/api/v1/tickets", () =>
        HttpResponse.json(
          aPage([
            aSummary({ id: 1, title: "Printer jam", status: "IN_PROGRESS", priority: "LOW", assignee: "sam" }),
            aSummary({ id: 2, title: "VPN down", assignee: null }),
          ]),
        ),
      ),
    );
    renderWithClient(<TicketListView params={DEFAULTS} onParamsChange={vi.fn()} />);

    const row = (await screen.findByRole("link", { name: "Printer jam" })).closest("tr")!;
    expect(within(row).getByText("In progress")).toBeInTheDocument();
    expect(within(row).getByText("Low")).toBeInTheDocument();
    expect(within(row).getByText("sam")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Printer jam" })).toHaveAttribute("href", "/tickets/1");
    expect(within(screen.getByRole("link", { name: "VPN down" }).closest("tr")!).getByText("Unassigned"))
      .toBeInTheDocument();
  });

  it("requests the list with the params from the URL", async () => {
    const { requests, handler } = captureListRequests();
    server.use(handler(aPage([aSummary()])));

    renderWithClient(
      <TicketListView
        params={{ q: "login", status: ["OPEN", "RESOLVED"], page: 1, size: 20, sort: "priority,desc" }}
        onParamsChange={vi.fn()}
      />,
    );

    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0].get("q")).toBe("login");
    expect(requests[0].getAll("status")).toEqual(["OPEN", "RESOLVED"]);
    expect(requests[0].get("page")).toBe("1");
    expect(requests[0].get("sort")).toBe("priority,desc");
  });

  it("shows an empty state", async () => {
    server.use(http.get("/api/v1/tickets", () => HttpResponse.json(aPage([]))));
    renderWithClient(<TicketListView params={DEFAULTS} onParamsChange={vi.fn()} />);

    expect(await screen.findByText("No tickets match.")).toBeInTheDocument();
  });

  it("shows server errors with a retry action", async () => {
    server.use(http.get("/api/v1/tickets", () => problem(500, "INTERNAL_ERROR")));
    renderWithClient(<TicketListView params={DEFAULTS} onParamsChange={vi.fn()} />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Something went wrong on our side.");
    expect(within(alert).getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });

  it("shows validation errors from the list endpoint (e.g. q too long)", async () => {
    server.use(http.get("/api/v1/tickets", () => problem(400, "VALIDATION_FAILED")));
    renderWithClient(<TicketListView params={DEFAULTS} onParamsChange={vi.fn()} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Please correct");
  });

  it("pages forward and back", async () => {
    server.use(
      http.get("/api/v1/tickets", () =>
        HttpResponse.json({ content: [aSummary()], page: { number: 1, size: 20, totalElements: 45, totalPages: 3 } }),
      ),
    );
    const onParamsChange = vi.fn();
    const { user } = renderWithClient(<TicketListView params={{ ...DEFAULTS, page: 1 }} onParamsChange={onParamsChange} />);

    expect(await screen.findByText("Page 2 of 3 (45 tickets)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(onParamsChange).toHaveBeenLastCalledWith({ ...DEFAULTS, page: 2 });
    await user.click(screen.getByRole("button", { name: "Previous" }));
    expect(onParamsChange).toHaveBeenLastCalledWith({ ...DEFAULTS, page: 0 });
  });

  it("changes the sort order and resets to the first page", async () => {
    server.use(http.get("/api/v1/tickets", () => HttpResponse.json(aPage([aSummary()]))));
    const onParamsChange = vi.fn();
    const { user } = renderWithClient(<TicketListView params={{ ...DEFAULTS, page: 2 }} onParamsChange={onParamsChange} />);

    await user.selectOptions(screen.getByLabelText("Sort"), "priority,desc");

    expect(onParamsChange).toHaveBeenLastCalledWith({ ...DEFAULTS, sort: "priority,desc", page: 0 });
  });
});

describe("TicketListView search and status filter (REQ-6/7, TS-FE-04)", () => {
  it("searches after typing and resets to the first page", async () => {
    server.use(http.get("/api/v1/tickets", () => HttpResponse.json(aPage([]))));
    const onParamsChange = vi.fn();
    const { user } = renderWithClient(
      <TicketListView params={{ ...DEFAULTS, page: 3 }} onParamsChange={onParamsChange} searchDebounceMs={0} />,
    );

    await user.type(screen.getByLabelText("Search"), "printer");

    await waitFor(() => expect(onParamsChange).toHaveBeenLastCalledWith({ ...DEFAULTS, q: "printer", page: 0 }));
  });

  it("limits the keyword to 100 characters", () => {
    server.use(http.get("/api/v1/tickets", () => HttpResponse.json(aPage([]))));
    renderWithClient(<TicketListView params={DEFAULTS} onParamsChange={vi.fn()} />);

    expect(screen.getByLabelText("Search")).toHaveAttribute("maxLength", "100");
  });

  it("toggles status filters on and off", async () => {
    server.use(http.get("/api/v1/tickets", () => HttpResponse.json(aPage([]))));
    const onParamsChange = vi.fn();
    const { user, rerender } = renderWithClient(
      <TicketListView params={{ ...DEFAULTS, page: 1 }} onParamsChange={onParamsChange} />,
    );

    await user.click(screen.getByRole("checkbox", { name: "Open" }));
    expect(onParamsChange).toHaveBeenLastCalledWith({ ...DEFAULTS, status: ["OPEN"], page: 0 });

    rerender(<TicketListView params={{ ...DEFAULTS, status: ["OPEN", "RESOLVED"] }} onParamsChange={onParamsChange} />);
    expect(screen.getByRole("checkbox", { name: "Open" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Resolved" })).toBeChecked();
    await user.click(screen.getByRole("checkbox", { name: "Open" }));
    expect(onParamsChange).toHaveBeenLastCalledWith({ ...DEFAULTS, status: ["RESOLVED"], page: 0 });
  });
});
