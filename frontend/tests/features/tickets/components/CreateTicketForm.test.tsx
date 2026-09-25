import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { aTicket, fieldError, problem } from "@tests/support/fixtures";
import { renderWithClient } from "@tests/support/render";
import { server } from "@tests/support/server";
import { CreateTicketForm } from "@/features/tickets/components/CreateTicketForm";

describe("CreateTicketForm (REQ-1, TS-FE-02)", () => {
  it("posts the ticket and reports the created ticket", async () => {
    let body: unknown;
    server.use(
      http.post("/api/v1/tickets", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(aTicket({ id: 99 }), { status: 201 });
      }),
    );
    const onCreated = vi.fn();
    const { user } = renderWithClient(<CreateTicketForm onCreated={onCreated} />);

    await user.type(screen.getByLabelText("Title"), "Printer jam");
    await user.type(screen.getByLabelText("Description"), "Paper stuck");
    await user.selectOptions(screen.getByLabelText("Priority"), "URGENT");
    await user.click(screen.getByRole("button", { name: "Create ticket" }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 99 })));
    expect(body).toEqual({ title: "Printer jam", description: "Paper stuck", priority: "URGENT", assignee: null });
  });

  it("sends the assignee when given", async () => {
    let body: Record<string, unknown> = {};
    server.use(
      http.post("/api/v1/tickets", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(aTicket(), { status: 201 });
      }),
    );
    const { user } = renderWithClient(<CreateTicketForm onCreated={vi.fn()} />);

    await user.type(screen.getByLabelText("Title"), "t");
    await user.type(screen.getByLabelText("Description"), "d");
    await user.type(screen.getByLabelText("Assignee (optional)"), "maria.lopez");
    await user.click(screen.getByRole("button", { name: "Create ticket" }));

    await waitFor(() => expect(body.assignee).toBe("maria.lopez"));
  });

  it("shows client-side hints and does not submit when required fields are empty", async () => {
    const onCreated = vi.fn();
    const { user } = renderWithClient(<CreateTicketForm onCreated={onCreated} />);

    await user.click(screen.getByRole("button", { name: "Create ticket" }));

    expect(await screen.findAllByText("This field is required.")).toHaveLength(2);
    expect(screen.getByLabelText("Title")).toHaveAttribute("aria-invalid", "true");
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("shows each server validation error next to its field", async () => {
    server.use(
      http.post("/api/v1/tickets", () =>
        problem(400, "VALIDATION_FAILED", {
          errors: [fieldError("title", "TOO_LONG"), fieldError("priority", "INVALID_VALUE")],
        }),
      ),
    );
    const { user } = renderWithClient(<CreateTicketForm onCreated={vi.fn()} />);

    await user.type(screen.getByLabelText("Title"), "t");
    await user.type(screen.getByLabelText("Description"), "d");
    await user.click(screen.getByRole("button", { name: "Create ticket" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Please correct the highlighted fields.");
    expect(screen.getByText("Must be at most 200 characters.")).toBeInTheDocument();
    expect(screen.getByText("This value is not valid.")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveAccessibleDescription("Must be at most 200 characters.");
  });

  it("shows errors that belong to no field in the form-level alert", async () => {
    server.use(
      http.post("/api/v1/tickets", () =>
        problem(400, "VALIDATION_FAILED", { errors: [fieldError("status", "UNKNOWN_FIELD")] }),
      ),
    );
    const { user } = renderWithClient(<CreateTicketForm onCreated={vi.fn()} />);

    await user.type(screen.getByLabelText("Title"), "t");
    await user.type(screen.getByLabelText("Description"), "d");
    await user.click(screen.getByRole("button", { name: "Create ticket" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("server message for status:UNKNOWN_FIELD");
  });

  it("shows a retryable message with the reference for server errors (TS-FE-12)", async () => {
    server.use(http.post("/api/v1/tickets", () => problem(500, "INTERNAL_ERROR", { detail: "NullPointerException" })));
    const { user } = renderWithClient(<CreateTicketForm onCreated={vi.fn()} />);

    await user.type(screen.getByLabelText("Title"), "t");
    await user.type(screen.getByLabelText("Description"), "d");
    await user.click(screen.getByRole("button", { name: "Create ticket" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Something went wrong on our side.");
    expect(alert).toHaveTextContent("Reference: corr-123");
    expect(alert).not.toHaveTextContent("NullPointerException");
  });
});
