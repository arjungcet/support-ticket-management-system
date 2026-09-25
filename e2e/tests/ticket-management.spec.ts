import { expect, test } from "@playwright/test";
import { apiCreateTicket, detailsHeading, listedTitles, uiCreateTicket, uniqueToken } from "./support";

test.describe("ticket management journeys", () => {
  test("J1 create ticket: the form creates an OPEN ticket and shows its details", async ({ page }) => {
    const title = `Printer jam ${uniqueToken()}`;

    await page.goto("/tickets/new");
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Paper stuck in tray 2");
    await page.getByLabel("Priority").selectOption("HIGH");
    await page.getByLabel("Assignee (optional)").fill("maria.lopez");
    await page.getByRole("button", { name: "Create ticket" }).click();

    await expect(page).toHaveURL(/\/tickets\/\d+$/);
    const id = Number(new URL(page.url()).pathname.split("/").pop());
    await expect(detailsHeading(page, id, title)).toBeVisible();
    const details = page.locator("dl.details");
    await expect(details).toContainText("Open");
    await expect(details).toContainText("High");
    await expect(details).toContainText("maria.lopez");
    await expect(page.getByText("Paper stuck in tray 2")).toBeVisible();
  });

  test("J2 list tickets: newly created tickets appear in the list, newest first", async ({ page, request }) => {
    const token = uniqueToken();
    await apiCreateTicket(request, { title: `First ${token}`, description: "one" });
    await apiCreateTicket(request, { title: `Second ${token}`, description: "two" });

    await page.goto(`/tickets?q=${token}`);

    await expect(listedTitles(page)).toHaveText([`Second ${token}`, `First ${token}`]);
    await expect(page.getByText("Page 1 of 1 (2 tickets)")).toBeVisible();
  });

  test("J3 open ticket: clicking a list row opens its details", async ({ page, request }) => {
    const token = uniqueToken();
    const ticket = await apiCreateTicket(request, { title: `Open me ${token}`, description: "Details body" });

    await page.goto(`/tickets?q=${token}`);
    await page.getByRole("link", { name: `Open me ${token}` }).click();

    await expect(page).toHaveURL(`/tickets/${ticket.id}`);
    await expect(detailsHeading(page, ticket.id, `Open me ${token}`)).toBeVisible();
    await expect(page.getByText("Details body")).toBeVisible();
  });

  test("J4 update ticket: edited fields are saved and survive a reload", async ({ page }) => {
    const token = uniqueToken();
    const id = await uiCreateTicket(page, `Old title ${token}`, "Old description");
    const form = page.getByRole("form", { name: "Edit ticket" });
    await expect(form.getByRole("button", { name: "Save changes" })).toBeDisabled();

    await form.getByLabel("Title").fill(`New title ${token}`);
    await form.getByLabel("Priority").selectOption("URGENT");
    await form.getByRole("button", { name: "Save changes" }).click();

    await expect(detailsHeading(page, id, `New title ${token}`)).toBeVisible();
    await page.reload();
    await expect(detailsHeading(page, id, `New title ${token}`)).toBeVisible();
    await expect(page.locator("dl.details")).toContainText("Urgent");
  });

  test("J5 change assignee: assign, then unassign", async ({ page }) => {
    await uiCreateTicket(page, `Assign ${uniqueToken()}`, "Needs an owner");
    const form = page.getByRole("form", { name: "Assignee" });
    const details = page.locator("dl.details");

    await form.getByLabel("Assignee").fill("sam.ops");
    await form.getByRole("button", { name: "Save assignee" }).click();
    await expect(details).toContainText("sam.ops");

    await page.getByRole("button", { name: "Unassign" }).click();
    await expect(details).toContainText("Unassigned");
    await page.reload();
    await expect(page.locator("dl.details")).toContainText("Unassigned");
  });

  test("J6 add comment: the comment appears and is kept after reload", async ({ page }) => {
    await uiCreateTicket(page, `Comment ${uniqueToken()}`, "Discuss here");
    const form = page.getByRole("form", { name: "Add comment" });

    await form.getByLabel("Your name").fill("maria.lopez");
    await form.getByLabel("Comment").fill("Reset the account lock; asked user to retry.");
    await form.getByRole("button", { name: "Add comment" }).click();

    const comments = page.getByRole("region", { name: "Comments" }).getByRole("listitem");
    await expect(comments).toHaveCount(1);
    await expect(comments.first()).toContainText("Reset the account lock; asked user to retry.");
    await expect(comments.first()).toContainText("maria.lopez");
    await expect(form.getByLabel("Comment")).toHaveValue("");

    await page.reload();
    await expect(page.getByRole("region", { name: "Comments" }).getByRole("listitem")).toHaveCount(1);
  });
});
