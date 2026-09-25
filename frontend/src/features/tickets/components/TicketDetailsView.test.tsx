import { screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import type { TicketResponse, TicketStatus } from "@/lib/api/types";
import { ALLOWED_TRANSITIONS, aComment, aPage, aTicket, fieldError, problem } from "@/test/fixtures";
import { renderWithClient } from "@/test/render";
import { server } from "@/test/server";
import { TicketDetailsView } from "./TicketDetailsView";

/** Serves one ticket (mutable) plus its comments; returns the recorded write requests. */
function serveTicket(initial: TicketResponse, comments = aPage([aComment()], { number: 0, size: 50 })) {
  const state = { ticket: initial, gets: 0 };
  server.use(
    http.get(`/api/v1/tickets/${initial.id}`, () => {
      state.gets += 1;
      return HttpResponse.json(state.ticket);
    }),
    http.get(`/api/v1/tickets/${initial.id}/comments`, () => HttpResponse.json(comments)),
  );
  return state;
}

async function renderTicket(ticket: TicketResponse) {
  const state = serveTicket(ticket);
  const rendered = renderWithClient(<TicketDetailsView ticketId={ticket.id} />);
  await screen.findByRole("heading", { level: 1, name: `#${ticket.id} ${ticket.title}` });
  return { ...rendered, state };
}

describe("TicketDetailsView (REQ-3, TS-FE-06)", () => {
  it("shows the ticket's fields and only the lifecycle timestamps that are set", async () => {
    await renderTicket(aTicket({ status: "RESOLVED", resolvedAt: "2026-09-25T11:00:00Z", assignee: "maria" }));

    expect(screen.getByText("User reports a 403 after password reset.")).toBeInTheDocument();
    expect(screen.getByText("High", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getAllByText("Resolved").length).toBeGreaterThan(0);
    expect(screen.getByText("Resolved", { selector: "dt" })).toBeInTheDocument();
    expect(screen.queryByText("Closed", { selector: "dt" })).not.toBeInTheDocument();
    expect(screen.queryByText("Cancelled", { selector: "dt" })).not.toBeInTheDocument();
  });

  it("shows a not-found state for TICKET_NOT_FOUND", async () => {
    server.use(http.get("/api/v1/tickets/7", () => problem(404, "TICKET_NOT_FOUND", { ticketId: 7 })));

    renderWithClient(<TicketDetailsView ticketId={7} />);

    expect(await screen.findByRole("heading", { name: "Ticket not found" })).toBeInTheDocument();
  });

  it("renders ticket text as text, never as HTML (TS-FE-14)", async () => {
    await renderTicket(aTicket({ description: '<img src=x onerror="alert(1)"><script>alert(2)</script>' }));

    expect(screen.getByText('<img src=x onerror="alert(1)"><script>alert(2)</script>')).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
    expect(document.querySelector("article script")).toBeNull();
  });
});

describe("status actions (REQ-11/12, TS-FE-07/08)", () => {
  const LABELS: Record<TicketStatus, string> = {
    OPEN: "Reopen",
    IN_PROGRESS: "Start progress",
    RESOLVED: "Resolve",
    CLOSED: "Close",
    CANCELLED: "Cancel ticket",
  };

  it.each(Object.keys(ALLOWED_TRANSITIONS) as TicketStatus[])(
    "in %s renders exactly the buttons for allowedTransitions",
    async (status) => {
      await renderTicket(aTicket({ status }));

      const actions = screen.getByRole("region", { name: "Status actions" });
      const buttons = within(actions).queryAllByRole("button").map((button) => button.textContent);
      expect(buttons).toEqual(ALLOWED_TRANSITIONS[status].map((target) => LABELS[target]));
    },
  );

  it("uses whatever the server allows, not a client-side table", async () => {
    await renderTicket(aTicket({ status: "OPEN", allowedTransitions: ["CANCELLED"] }));

    const actions = screen.getByRole("region", { name: "Status actions" });
    expect(within(actions).getAllByRole("button").map((button) => button.textContent)).toEqual(["Cancel ticket"]);
  });

  it("posts the transition with the current version and shows the new state", async () => {
    const { user, state } = await renderTicket(aTicket({ status: "IN_PROGRESS", version: 4 }));
    let body: unknown;
    server.use(
      http.post("/api/v1/tickets/42/status-transitions", async ({ request }) => {
        body = await request.json();
        state.ticket = aTicket({ status: "RESOLVED", version: 5, resolvedAt: "2026-09-25T12:00:00Z" });
        return HttpResponse.json(state.ticket);
      }),
    );

    await user.click(screen.getByRole("button", { name: "Resolve" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Close" })).toBeInTheDocument());
    expect(body).toEqual({ version: 4, targetStatus: "RESOLVED" });
    expect(screen.queryByRole("button", { name: "Resolve" })).not.toBeInTheDocument();
  });

  it("shows the reason for a rejected transition and reloads the ticket", async () => {
    const { user, state } = await renderTicket(aTicket({ status: "OPEN" }));
    server.use(
      http.post("/api/v1/tickets/42/status-transitions", () => {
        state.ticket = aTicket({ status: "CANCELLED", version: 3 });
        return problem(409, "TICKET_INVALID_TRANSITION", {
          detail: "Ticket 42 cannot move from CANCELLED to IN_PROGRESS.",
          currentStatus: "CANCELLED",
          targetStatus: "IN_PROGRESS",
          allowedTransitions: [],
        });
      }),
    );
    const getsBefore = state.gets;

    await user.click(screen.getByRole("button", { name: "Start progress" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Ticket 42 cannot move from CANCELLED to IN_PROGRESS.");
    await waitFor(() => expect(state.gets).toBeGreaterThan(getsBefore));
    expect(await screen.findByText("No further status changes are possible.")).toBeInTheDocument();
  });

  it("offers a reload when the ticket changed meanwhile", async () => {
    const { user, state } = await renderTicket(aTicket({ status: "OPEN" }));
    server.use(http.post("/api/v1/tickets/42/status-transitions", () => problem(409, "TICKET_CONCURRENT_MODIFICATION")));

    await user.click(screen.getByRole("button", { name: "Start progress" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("This ticket was changed by someone else.");
    const getsBefore = state.gets;
    await user.click(within(alert).getByRole("button", { name: "Reload ticket" }));
    await waitFor(() => expect(state.gets).toBeGreaterThan(getsBefore));
  });
});

describe("edit ticket (REQ-4, TS-FE-09)", () => {
  it("disables Save until something changes, then sends only changed fields with the version", async () => {
    const { user } = await renderTicket(aTicket({ version: 2 }));
    let body: unknown;
    server.use(
      http.patch("/api/v1/tickets/42", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(aTicket({ title: "Portal login broken", version: 3 }));
      }),
    );
    const save = screen.getByRole("button", { name: "Save changes" });
    expect(save).toBeDisabled();

    const title = screen.getByLabelText("Title");
    await user.clear(title);
    await user.type(title, "Portal login broken");
    await user.click(save);

    await waitFor(() => expect(body).toEqual({ version: 2, title: "Portal login broken" }));
    expect(await screen.findByRole("heading", { level: 1, name: "#42 Portal login broken" })).toBeInTheDocument();
  });

  it("shows server field errors next to the fields", async () => {
    const { user } = await renderTicket(aTicket());
    server.use(
      http.patch("/api/v1/tickets/42", () =>
        problem(400, "VALIDATION_FAILED", { errors: [fieldError("description", "TOO_LONG")] }),
      ),
    );

    await user.type(screen.getByLabelText("Description"), " more");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Must be at most 5000 characters.")).toBeInTheDocument();
  });

  it("keeps the user's input and offers a reload on a version conflict", async () => {
    const { user, state } = await renderTicket(aTicket());
    server.use(http.patch("/api/v1/tickets/42", () => problem(409, "TICKET_CONCURRENT_MODIFICATION")));
    const title = screen.getByLabelText("Title");
    await user.clear(title);
    await user.type(title, "My unsaved title");

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("This ticket was changed by someone else.");
    state.ticket = aTicket({ version: 3, description: "Changed by a colleague" });
    await user.click(within(alert).getByRole("button", { name: "Reload ticket" }));
    expect(await screen.findByText("Changed by a colleague")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveValue("My unsaved title");
  });

  it("shows TICKET_NOT_EDITABLE when the server refuses the edit", async () => {
    const { user } = await renderTicket(aTicket());
    server.use(http.patch("/api/v1/tickets/42", () => problem(422, "TICKET_NOT_EDITABLE", { currentStatus: "CLOSED" })));

    await user.type(screen.getByLabelText("Title"), "!");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Closed or cancelled tickets can't be edited.");
  });

  it.each(["CLOSED", "CANCELLED"] as const)("hides edit and assignee controls for %s tickets", async (status) => {
    await renderTicket(aTicket({ status }));

    expect(screen.queryByRole("form", { name: "Edit ticket" })).not.toBeInTheDocument();
    expect(screen.queryByRole("form", { name: "Assignee" })).not.toBeInTheDocument();
  });
});

describe("assignee (REQ-4, TS-FE-10)", () => {
  it("assigns with PUT /assignee and the current version", async () => {
    const { user } = await renderTicket(aTicket({ version: 2 }));
    let body: unknown;
    server.use(
      http.put("/api/v1/tickets/42/assignee", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(aTicket({ assignee: "maria.lopez", version: 3 }));
      }),
    );
    const form = screen.getByRole("form", { name: "Assignee" });

    await user.type(within(form).getByLabelText("Assignee"), "maria.lopez");
    await user.click(within(form).getByRole("button", { name: "Save assignee" }));

    await waitFor(() => expect(body).toEqual({ version: 2, assignee: "maria.lopez" }));
    expect(await screen.findByText("maria.lopez", { selector: "dd" })).toBeInTheDocument();
  });

  it("unassigns by sending assignee null", async () => {
    const { user } = await renderTicket(aTicket({ assignee: "maria.lopez", version: 5 }));
    let body: unknown;
    server.use(
      http.put("/api/v1/tickets/42/assignee", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(aTicket({ assignee: null, version: 6 }));
      }),
    );

    await user.click(screen.getByRole("button", { name: "Unassign" }));

    await waitFor(() => expect(body).toEqual({ version: 5, assignee: null }));
    expect(await screen.findByText("Unassigned", { selector: "dd" })).toBeInTheDocument();
  });

  it("shows the server's field error for a too-long assignee", async () => {
    const { user } = await renderTicket(aTicket());
    server.use(
      http.put("/api/v1/tickets/42/assignee", () =>
        problem(400, "VALIDATION_FAILED", { errors: [fieldError("assignee", "TOO_LONG")] }),
      ),
    );
    const form = screen.getByRole("form", { name: "Assignee" });

    await user.type(within(form).getByLabelText("Assignee"), "someone");
    await user.click(within(form).getByRole("button", { name: "Save assignee" }));

    expect(await within(form).findByText("Must be at most 100 characters.")).toBeInTheDocument();
  });
});

describe("comments (REQ-5, TS-FE-11)", () => {
  it("lists comments oldest first", async () => {
    serveTicket(
      aTicket(),
      aPage([aComment({ id: 1, body: "first" }), aComment({ id: 2, body: "second", author: "sam" })], {
        number: 0,
        size: 50,
      }),
    );
    renderWithClient(<TicketDetailsView ticketId={42} />);

    const list = await screen.findByRole("list");
    expect(within(list).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      expect.stringContaining("first"),
      expect.stringContaining("second"),
    ]);
  });

  it("adds a comment, clears the text and remembers the author", async () => {
    const { user } = await renderTicket(aTicket());
    let body: unknown;
    server.use(
      http.post("/api/v1/tickets/42/comments", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(aComment({ id: 9, body: "On it" }), { status: 201 });
      }),
    );
    const form = screen.getByRole("form", { name: "Add comment" });

    await user.type(within(form).getByLabelText("Your name"), "maria.lopez");
    await user.type(within(form).getByLabelText("Comment"), "On it");
    await user.click(within(form).getByRole("button", { name: "Add comment" }));

    await waitFor(() => expect(body).toEqual({ author: "maria.lopez", body: "On it" }));
    await waitFor(() => expect(within(form).getByLabelText("Comment")).toHaveValue(""));
    expect(within(form).getByLabelText("Your name")).toHaveValue("maria.lopez");
    expect(window.localStorage.getItem("supportdesk.commentAuthor")).toBe("maria.lopez");
  });

  it("requires author and comment text before sending", async () => {
    const { user } = await renderTicket(aTicket());
    const form = screen.getByRole("form", { name: "Add comment" });

    await user.click(within(form).getByRole("button", { name: "Add comment" }));

    expect(await within(form).findAllByText("This field is required.")).toHaveLength(2);
  });

  it("shows TICKET_NOT_COMMENTABLE and reloads the ticket, which hides the form", async () => {
    const { user, state } = await renderTicket(aTicket());
    server.use(
      http.post("/api/v1/tickets/42/comments", () => {
        state.ticket = aTicket({ status: "CLOSED", version: 5 });
        return problem(422, "TICKET_NOT_COMMENTABLE", { currentStatus: "CLOSED" });
      }),
    );
    const form = screen.getByRole("form", { name: "Add comment" });

    await user.type(within(form).getByLabelText("Your name"), "a");
    await user.type(within(form).getByLabelText("Comment"), "late");
    await user.click(within(form).getByRole("button", { name: "Add comment" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Closed or cancelled tickets can't receive new comments.");
    await waitFor(() => expect(screen.queryByRole("form", { name: "Add comment" })).not.toBeInTheDocument());
  });

  it.each(["CLOSED", "CANCELLED"] as const)("hides the comment form for %s tickets", async (status) => {
    await renderTicket(aTicket({ status }));

    expect(screen.queryByRole("form", { name: "Add comment" })).not.toBeInTheDocument();
  });
});
