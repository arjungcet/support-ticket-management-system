import { expect, test, type Page } from "@playwright/test";
import { apiCreateTicket, uniqueToken } from "./support";

/** Security review M-2: headers on real responses, and the CSP must not block the app itself. */
test.describe("security headers", () => {
  test("pages send a nonce-based CSP and the baseline headers, without X-Powered-By", async ({ request }) => {
    const first = await request.get("/tickets");
    const second = await request.get("/tickets");
    const headers = first.headers();

    expect(headers["x-frame-options"]).toBe("DENY");
    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
    expect(headers["strict-transport-security"]).toBe("max-age=31536000");
    expect(headers["x-powered-by"]).toBeUndefined();

    const csp = headers["content-security-policy"];
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+' 'strict-dynamic'/);
    expect(csp).not.toContain("unsafe-inline");
    // A fresh nonce per response.
    const nonce = (policy: string) => /'nonce-([^']+)'/.exec(policy)?.[1];
    expect(nonce(csp)).not.toEqual(nonce(second.headers()["content-security-policy"]));
  });

  test("API responses carry the static headers too", async ({ request }) => {
    const headers = (await request.get("/api/v1/tickets")).headers();

    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["x-frame-options"]).toBe("DENY");
  });

  test("the CSP blocks nothing the app needs: list, details, create and comment work without violations", async ({
    page,
    request,
  }) => {
    const violations = collectCspViolations(page);
    const token = uniqueToken();
    const ticket = await apiCreateTicket(request, { title: `CSP ${token}`, description: "d", priority: "HIGH" });

    await page.goto(`/tickets?q=${token}`);
    await page.getByRole("link", { name: `CSP ${token}` }).click();
    await expect(page.getByRole("heading", { level: 1 })).toContainText(`CSP ${token}`);
    const form = page.getByRole("form", { name: "Add comment" });
    await form.getByLabel("Your name").fill("csp-check");
    await form.getByLabel("Comment").fill(`comment ${token}`);
    await form.getByRole("button", { name: "Add comment" }).click();
    await expect(page.getByText(`comment ${token}`)).toBeVisible();

    await page.goto("/tickets/new");
    await expect(page.getByRole("button", { name: "Create ticket" })).toBeEnabled();

    expect(ticket.id).toBeGreaterThan(0);
    expect(violations).toEqual([]);
  });
});

function collectCspViolations(page: Page): string[] {
  const violations: string[] = [];
  page.on("console", (message) => {
    if (/Content Security Policy|Content-Security-Policy/i.test(message.text())) violations.push(message.text());
  });
  page.on("pageerror", (error) => violations.push(`pageerror: ${error.message}`));
  return violations;
}
