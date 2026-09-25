import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { apiCreateTicket, apiCreateInStatus, uniqueToken } from "./support";

/**
 * D-4: WCAG 2.2 level AA, checked automatically with axe-core on every page type, in light and dark mode. Automated
 * rules cover only part of WCAG (roughly contrast, names, roles, structure); keyboard use and screen-reader flow are
 * covered by the role-based journeys, not proven complete.
 */
const WCAG_AA = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

async function expectNoViolations(page: Page, what: string) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_AA).analyze();
  const summary = results.violations.map(
    (v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`,
  );
  expect(summary, `${what}: WCAG 2.2 AA violations`).toEqual([]);
}

for (const colorScheme of ["light", "dark"] as const) {
  test.describe(`accessibility, ${colorScheme} mode`, () => {
    test.use({ colorScheme });

    test("ticket list with filters, badges and pagination", async ({ page, request }) => {
      const token = uniqueToken();
      await apiCreateTicket(request, { title: `A11y ${token}`, description: "d", priority: "URGENT" });
      await apiCreateInStatus(request, "RESOLVED", { title: `A11y resolved ${token}`, description: "d" });

      await page.goto(`/tickets?q=${token}&status=OPEN&status=RESOLVED`);
      await expect(page.getByRole("link", { name: `A11y ${token}` })).toBeVisible();

      await expectNoViolations(page, "ticket list");
    });

    test("ticket details of an open ticket, with all forms and a comment", async ({ page, request }) => {
      const ticket = await apiCreateTicket(request, { title: `A11y details ${uniqueToken()}`, description: "d" });
      await request.post(`/api/v1/tickets/${ticket.id}/comments`, { data: { author: "agent", body: "Looking" } });

      await page.goto(`/tickets/${ticket.id}`);
      await expect(page.getByText("Looking")).toBeVisible();

      await expectNoViolations(page, "open ticket details");
    });

    test("ticket details of a closed ticket", async ({ page, request }) => {
      const ticket = await apiCreateInStatus(request, "CLOSED", { title: `A11y closed ${uniqueToken()}`, description: "d" });

      await page.goto(`/tickets/${ticket.id}`);
      await expect(page.getByText("No further status changes are possible.")).toBeVisible();

      await expectNoViolations(page, "closed ticket details");
    });

    test("new ticket form, including shown validation errors", async ({ page }) => {
      await page.goto("/tickets/new");
      await page.getByRole("button", { name: "Create ticket" }).click();
      await expect(page.getByText("This field is required.").first()).toBeVisible();

      await expectNoViolations(page, "new ticket form with errors");
    });

    test("not-found pages", async ({ page }) => {
      await page.goto("/tickets/999999999");
      await expect(page.getByRole("heading", { name: "Ticket not found" })).toBeVisible();
      await expectNoViolations(page, "ticket not found");

      await page.goto("/no-such-page");
      await expect(page.getByRole("heading", { name: "Page not found" })).toBeVisible();
      await expectNoViolations(page, "page not found");
    });
  });
}
