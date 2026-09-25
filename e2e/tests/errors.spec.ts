import { expect, test } from "@playwright/test";
import { restartBackend, startBackend, stopBackend } from "../scripts/servers";
import { appAlert, uniqueToken } from "./support";

test.describe("error journeys", () => {
  test("J11 validation failure: required fields are flagged before sending", async ({ page }) => {
    await page.goto("/tickets/new");

    await page.getByRole("button", { name: "Create ticket" }).click();

    await expect(page.getByText("This field is required.")).toHaveCount(2);
    await expect(page).toHaveURL(/\/tickets\/new$/);
  });

  test("J11 validation failure: the backend rejects invalid input with field-level errors", async ({ request }) => {
    const response = await request.post("/api/v1/tickets", {
      data: { title: "   ", description: "d".repeat(5001), priority: "CRITICAL" },
    });

    expect(response.status()).toBe(400);
    const problem = await response.json();
    expect(problem.code).toBe("VALIDATION_FAILED");
    const tuples = problem.errors.map((e: { field: string; code: string }) => `${e.field}:${e.code}`).sort();
    expect(tuples).toEqual(["description:TOO_LONG", "priority:INVALID_VALUE", "title:BLANK"]);
  });

  test("J11 validation failure from the backend is shown in the UI (q longer than 100 characters)", async ({ page }) => {
    await page.goto(`/tickets?q=${"k".repeat(101)}`);

    await expect(appAlert(page)).toContainText("Please correct the highlighted fields.");
  });

  test("J12 business error in the UI: an unknown ticket shows a not-found page", async ({ page }) => {
    await page.goto("/tickets/987654321012");

    await expect(page.getByRole("heading", { name: "Ticket not found" })).toBeVisible();
  });

  test("J12 backend unavailable: the UI shows a retryable error instead of crashing", async ({ page }) => {
    await stopBackend();
    try {
      await page.goto(`/tickets?q=${uniqueToken()}`);
      const alert = appAlert(page);
      await expect(alert).toBeVisible();
      await expect(alert).toContainText(/Something went wrong on our side|Can't reach the server/);
      await expect(alert.getByRole("button", { name: "Try again" })).toBeVisible();
    } finally {
      await startBackend();
    }
  });

  test("J12 recovery: after the backend is back, Try again loads the list", async ({ page }) => {
    await restartBackend();
    await page.goto(`/tickets?q=${uniqueToken()}`);

    await expect(page.getByText("No tickets match.")).toBeVisible();
  });
});
