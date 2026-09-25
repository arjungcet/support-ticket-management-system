import { expect, test } from "@playwright/test";
import { appAlert, apiCreateInStatus, detailsHeading, uiCreateTicket, uniqueToken } from "./support";

const TRANSITION_LABELS = /^(Start progress|Resolve|Close|Cancel ticket|Reopen)$/;

/** Transition buttons only (the region can also contain an error alert with its own Reload button). */
function statusButtons(page: import("@playwright/test").Page) {
  return page.getByRole("region", { name: "Status actions" }).getByRole("button", { name: TRANSITION_LABELS });
}

test.describe("state machine journeys", () => {
  test("J9 valid transitions: OPEN → IN_PROGRESS → RESOLVED → CLOSED through the UI", async ({ page }) => {
    const title = `Lifecycle ${uniqueToken()}`;
    const id = await uiCreateTicket(page, title, "Walk the happy path");
    const details = page.locator("dl.details");

    await expect(statusButtons(page)).toHaveText(["Start progress", "Cancel ticket"]);
    await page.getByRole("button", { name: "Start progress" }).click();
    await expect(details).toContainText("In progress");
    await expect(statusButtons(page)).toHaveText(["Resolve", "Cancel ticket"]);

    await page.getByRole("button", { name: "Resolve" }).click();
    await expect(details).toContainText("Resolved");
    await expect(statusButtons(page)).toHaveText(["Close"]);

    await page.getByRole("button", { name: "Close" }).click();
    await expect(details).toContainText("Closed");
    await expect(statusButtons(page)).toHaveCount(0);
    await expect(page.getByText("No further status changes are possible.")).toBeVisible();
    await expect(page.getByRole("form", { name: "Edit ticket" })).toHaveCount(0);
    await expect(page.getByRole("form", { name: "Add comment" })).toHaveCount(0);

    await page.reload();
    await expect(detailsHeading(page, id, title)).toBeVisible();
    await expect(page.locator("dl.details")).toContainText("Closed");
  });

  test("J9 valid transition: OPEN → CANCELLED", async ({ page }) => {
    await uiCreateTicket(page, `Cancel ${uniqueToken()}`, "Not needed");

    await page.getByRole("button", { name: "Cancel ticket" }).click();

    await expect(page.locator("dl.details")).toContainText("Cancelled");
    await expect(statusButtons(page)).toHaveCount(0);
  });

  test("J10 invalid transition from a stale tab is rejected and explained", async ({ browser }) => {
    const tabA = await (await browser.newContext()).newPage();
    const tabB = await (await browser.newContext()).newPage();
    const id = await uiCreateTicket(tabA, `Stale ${uniqueToken()}`, "Two agents");
    await tabB.goto(`/tickets/${id}`);
    await expect(tabB.getByRole("button", { name: "Start progress" })).toBeVisible();

    await tabA.getByRole("button", { name: "Cancel ticket" }).click();
    await expect(tabA.locator("dl.details")).toContainText("Cancelled");

    await tabB.getByRole("button", { name: "Start progress" }).click();

    // B's version is stale, so the version conflict is reported first (api-contract §1.3).
    await expect(appAlert(tabB)).toContainText("This ticket was changed by someone else.");
    await tabB.getByRole("button", { name: "Reload ticket" }).click();
    await expect(tabB.locator("dl.details")).toContainText("Cancelled");
    await expect(statusButtons(tabB)).toHaveCount(0);
  });

  test("J10 after reloading a conflicted ticket, the stale conflict message is cleared", async ({ browser }) => {
    const tabA = await (await browser.newContext()).newPage();
    const tabB = await (await browser.newContext()).newPage();
    const id = await uiCreateTicket(tabA, `Stale message ${uniqueToken()}`, "Two agents");
    await tabB.goto(`/tickets/${id}`);
    await expect(tabB.getByRole("button", { name: "Start progress" })).toBeVisible();
    await tabA.getByRole("button", { name: "Cancel ticket" }).click();
    await expect(tabA.locator("dl.details")).toContainText("Cancelled");
    await tabB.getByRole("button", { name: "Start progress" }).click();
    await expect(appAlert(tabB)).toContainText("This ticket was changed by someone else.");

    await tabB.getByRole("button", { name: "Reload ticket" }).click();
    await expect(tabB.locator("dl.details")).toContainText("Cancelled");

    // The user now sees the current ticket, so "changed by someone else — reload" no longer applies.
    await expect(appAlert(tabB)).toHaveCount(0);
  });

  test("J10 invalid transitions sent directly to the API are rejected (CLOSED/RESOLVED/CANCELLED → OPEN)", async ({
    request,
  }) => {
    for (const status of ["CLOSED", "RESOLVED", "CANCELLED"] as const) {
      const ticket = await apiCreateInStatus(request, status, { title: `Bypass ${status}`, description: "curl" });

      const response = await request.post(`/api/v1/tickets/${ticket.id}/status-transitions`, {
        data: { version: ticket.version, targetStatus: "OPEN" },
      });

      expect(response.status(), `${status} → OPEN`).toBe(409);
      expect(response.headers()["content-type"]).toContain("application/problem+json");
      const problem = await response.json();
      expect(problem).toMatchObject({ code: "TICKET_INVALID_TRANSITION", currentStatus: status, targetStatus: "OPEN" });
      const after = await (await request.get(`/api/v1/tickets/${ticket.id}`)).json();
      expect(after).toMatchObject({ status, version: ticket.version });
    }
  });
});
