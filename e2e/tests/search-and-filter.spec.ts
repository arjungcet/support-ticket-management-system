import { expect, test } from "@playwright/test";
import { apiCreateInStatus, listedTitles, uniqueToken } from "./support";

test.describe("search and filter journeys", () => {
  test("J7 search: typing a keyword filters by title and description, case-insensitively, and survives reload", async ({
    page,
    request,
  }) => {
    const token = uniqueToken();
    await apiCreateInStatus(request, "OPEN", { title: `Login fails ${token}`, description: "portal" });
    await apiCreateInStatus(request, "OPEN", { title: `Printer ${token}`, description: "see LOGIN screen" });
    await apiCreateInStatus(request, "OPEN", { title: `Unrelated ${token}`, description: "nothing" });

    await page.goto(`/tickets?q=${token}`);
    await expect(listedTitles(page)).toHaveCount(3);

    await page.getByLabel("Search").fill(`${token.toUpperCase()}`);
    await expect(page).toHaveURL(new RegExp(`q=${token.toUpperCase()}`));
    await expect(listedTitles(page)).toHaveCount(3);

    // Keyword that only two of this test's tickets contain; the token keeps other tests' data out of the result.
    await page.goto(`/tickets?q=${token}`);
    await page.getByLabel("Search").fill("login");
    await expect(page).toHaveURL(/q=login/);
    await expect(page.getByRole("link", { name: `Login fails ${token}` })).toBeVisible();
    await expect(page.getByRole("link", { name: `Printer ${token}` })).toBeVisible();
    await expect(page.getByRole("link", { name: `Unrelated ${token}` })).toHaveCount(0);

    await page.reload();
    await expect(page.getByLabel("Search")).toHaveValue("login");
    await expect(page.getByRole("link", { name: `Unrelated ${token}` })).toHaveCount(0);
  });

  test("J7 search: no match shows the empty state", async ({ page }) => {
    await page.goto(`/tickets?q=${uniqueToken()}`);

    await expect(page.getByText("No tickets match.")).toBeVisible();
  });

  test("J8 filter: status checkboxes restrict the list and are kept in the URL", async ({ page, request }) => {
    const token = uniqueToken();
    await apiCreateInStatus(request, "OPEN", { title: `Open ${token}`, description: "d" });
    await apiCreateInStatus(request, "IN_PROGRESS", { title: `Working ${token}`, description: "d" });
    await apiCreateInStatus(request, "CLOSED", { title: `Done ${token}`, description: "d" });

    await page.goto(`/tickets?q=${token}`);
    await expect(listedTitles(page)).toHaveCount(3);

    // The checkboxes reflect the URL, so their state changes once the URL update completes: click, then wait.
    await page.getByRole("checkbox", { name: "In progress" }).click();
    await expect(page.getByRole("checkbox", { name: "In progress" })).toBeChecked();
    await expect(page).toHaveURL(/status=IN_PROGRESS/);
    await expect(listedTitles(page)).toHaveText([`Working ${token}`]);

    await page.getByRole("checkbox", { name: "Closed" }).click();
    await expect(page.getByRole("checkbox", { name: "Closed" })).toBeChecked();
    await expect(listedTitles(page)).toHaveCount(2);

    await page.reload();
    await expect(page.getByRole("checkbox", { name: "In progress" })).toBeChecked();
    await expect(page.getByRole("checkbox", { name: "Closed" })).toBeChecked();
    await expect(page.getByRole("link", { name: `Open ${token}` })).toHaveCount(0);
  });
});
