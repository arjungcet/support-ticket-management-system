import { expect, type APIRequestContext, type Page } from "@playwright/test";

export type Status = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | "CANCELLED";

export interface Ticket {
  id: number;
  title: string;
  status: Status;
  version: number;
  assignee: string | null;
  allowedTransitions: Status[];
}

const API = "/api/v1";
const PATH_FROM_OPEN: Record<Status, Status[]> = {
  OPEN: [],
  IN_PROGRESS: ["IN_PROGRESS"],
  RESOLVED: ["IN_PROGRESS", "RESOLVED"],
  CLOSED: ["IN_PROGRESS", "RESOLVED", "CLOSED"],
  CANCELLED: ["CANCELLED"],
};

/** Letters only, so a token never matches the search terms used in the journeys. */
export function uniqueToken(): string {
  const letters = "bcdfghjkmnpqrstvwxz";
  let token = "tk";
  for (let i = 0; i < 10; i++) token += letters[Math.floor(Math.random() * letters.length)];
  return token;
}

/** Test data is created through the public API (via the same /api proxy the browser uses), never the database. */
export async function apiCreateTicket(request: APIRequestContext, body: Record<string, unknown>): Promise<Ticket> {
  const response = await request.post(`${API}/tickets`, { data: body });
  expect(response.status(), `create ticket: ${await response.text()}`).toBe(201);
  return (await response.json()) as Ticket;
}

export async function apiTransition(request: APIRequestContext, ticket: Ticket, target: Status): Promise<Ticket> {
  const response = await request.post(`${API}/tickets/${ticket.id}/status-transitions`, {
    data: { version: ticket.version, targetStatus: target },
  });
  expect(response.status(), `transition to ${target}: ${await response.text()}`).toBe(200);
  return (await response.json()) as Ticket;
}

export async function apiCreateInStatus(
  request: APIRequestContext,
  status: Status,
  body: Record<string, unknown>,
): Promise<Ticket> {
  let ticket = await apiCreateTicket(request, body);
  for (const next of PATH_FROM_OPEN[status]) ticket = await apiTransition(request, ticket, next);
  return ticket;
}

/** Creates a ticket through the UI form and returns its id (from the details URL). */
export async function uiCreateTicket(page: Page, title: string, description: string): Promise<number> {
  await page.goto("/tickets/new");
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("Description").fill(description);
  await page.getByRole("button", { name: "Create ticket" }).click();
  await expect(page).toHaveURL(/\/tickets\/\d+$/);
  return Number(new URL(page.url()).pathname.split("/").pop());
}

export function detailsHeading(page: Page, id: number, title: string) {
  return page.getByRole("heading", { level: 1, name: `#${id} ${title}` });
}

export function listedTitles(page: Page) {
  return page.getByRole("table").getByRole("link");
}

/**
 * The application's own error alert. Next.js also renders a hidden route announcer with role="alert", so a bare
 * getByRole("alert") is ambiguous.
 */
export function appAlert(page: Page) {
  return page.locator(".alert[role=alert]");
}
