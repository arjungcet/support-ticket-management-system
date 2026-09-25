import { STATIC_SECURITY_HEADERS, contentSecurityPolicy } from "@/lib/security/headers";

function directives(policy: string): Map<string, string> {
  return new Map(
    policy.split("; ").map((directive) => {
      const [name, ...values] = directive.split(" ");
      return [name, values.join(" ")];
    }),
  );
}

describe("contentSecurityPolicy", () => {
  it("allows scripts only from this origin or with the request nonce", () => {
    const csp = directives(contentSecurityPolicy("abc123", false));

    expect(csp.get("script-src")).toBe("'self' 'nonce-abc123' 'strict-dynamic'");
    expect(csp.get("style-src")).toBe("'self' 'nonce-abc123'");
    expect(csp.get("default-src")).toBe("'self'");
  });

  it("forbids framing, plugins and foreign form targets", () => {
    const csp = directives(contentSecurityPolicy("n", false));

    expect(csp.get("frame-ancestors")).toBe("'none'");
    expect(csp.get("object-src")).toBe("'none'");
    expect(csp.get("form-action")).toBe("'self'");
    expect(csp.get("base-uri")).toBe("'self'");
  });

  it("production allows neither unsafe-inline nor unsafe-eval", () => {
    const production = contentSecurityPolicy("n", false);

    expect(production).not.toContain("unsafe-inline");
    expect(production).not.toContain("unsafe-eval");
  });

  it("development relaxes only what next dev needs: eval for scripts, inline styles for the dev overlay", () => {
    const development = directives(contentSecurityPolicy("abc123", true));

    expect(development.get("script-src")).toBe("'self' 'nonce-abc123' 'strict-dynamic' 'unsafe-eval'");
    expect(development.get("script-src")).not.toContain("unsafe-inline");
    // No nonce here: a nonce would make browsers ignore 'unsafe-inline'.
    expect(development.get("style-src")).toBe("'self' 'unsafe-inline'");
  });

  it("does not upgrade requests to HTTPS, so plain-HTTP internal hosts keep working", () => {
    expect(contentSecurityPolicy("n", false)).not.toContain("upgrade-insecure-requests");
  });
});

describe("STATIC_SECURITY_HEADERS", () => {
  it("sets the baseline headers from security review M-2", () => {
    const headers = Object.fromEntries(STATIC_SECURITY_HEADERS.map(({ key, value }) => [key, value]));

    expect(headers).toEqual({
      "X-Frame-Options": "DENY",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "strict-origin-when-cross-origin",
      "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=()",
      "Strict-Transport-Security": "max-age=31536000",
    });
  });
});
