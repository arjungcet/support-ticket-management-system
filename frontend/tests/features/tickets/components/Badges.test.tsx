import { render, screen } from "@testing-library/react";
import { PriorityBadge, StatusBadge } from "@/features/tickets/components/Badges";
import { TICKET_PRIORITIES, TICKET_STATUSES } from "@/lib/api/types";

describe("StatusBadge / PriorityBadge", () => {
  it.each([
    ["OPEN", "Open", "status-open"],
    ["IN_PROGRESS", "In progress", "status-in-progress"],
    ["RESOLVED", "Resolved", "status-resolved"],
    ["CLOSED", "Closed", "status-closed"],
    ["CANCELLED", "Cancelled", "status-cancelled"],
  ] as const)("shows %s as the plain label %s with its colour class", (status, label, className) => {
    render(<StatusBadge status={status} />);

    expect(screen.getByText(label)).toHaveClass("badge", className);
  });

  it("covers every status and priority the API defines", () => {
    render(
      <>
        {TICKET_STATUSES.map((status) => (
          <StatusBadge key={status} status={status} />
        ))}
        {TICKET_PRIORITIES.map((priority) => (
          <PriorityBadge key={priority} priority={priority} />
        ))}
      </>,
    );

    expect(screen.getByText("Low")).toHaveClass("badge", "priority-low");
    expect(screen.getByText("Medium")).toHaveClass("priority-medium");
    expect(screen.getByText("High")).toHaveClass("priority-high");
    expect(screen.getByText("Urgent")).toHaveClass("priority-urgent");
    expect(document.querySelectorAll(".badge")).toHaveLength(TICKET_STATUSES.length + TICKET_PRIORITIES.length);
  });
});
