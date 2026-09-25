import { expect, test } from "@playwright/test";
import { restartBackend } from "../scripts/servers";
import { detailsHeading, uiCreateTicket, uniqueToken } from "./support";

test("J13 persistence: tickets, status and comments survive a backend restart", async ({ page }) => {
  const title = `Durable ${uniqueToken()}`;
  const id = await uiCreateTicket(page, title, "Must survive a restart");
  await page.getByRole("button", { name: "Start progress" }).click();
  await expect(page.locator("dl.details")).toContainText("In progress");
  const form = page.getByRole("form", { name: "Add comment" });
  await form.getByLabel("Your name").fill("maria.lopez");
  await form.getByLabel("Comment").fill("Written before the restart");
  await form.getByRole("button", { name: "Add comment" }).click();
  await expect(page.getByRole("region", { name: "Comments" }).getByRole("listitem")).toHaveCount(1);

  await restartBackend();
  await page.reload();

  await expect(detailsHeading(page, id, title)).toBeVisible();
  await expect(page.locator("dl.details")).toContainText("In progress");
  await expect(page.getByRole("region", { name: "Comments" })).toContainText("Written before the restart");
  await expect(page.getByRole("button", { name: "Resolve" })).toBeVisible();
});
