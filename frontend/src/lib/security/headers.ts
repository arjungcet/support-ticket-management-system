/**
 * Security headers (security review M-2). The page CSP carries a per-request nonce and is set by src/proxy.ts; the
 * static headers apply to every response via next.config.ts.
 */

/** `upgrade-insecure-requests` is left out on purpose: v1 may run over plain HTTP on internal hosts (ADR-0002). */
export function contentSecurityPolicy(nonce: string, isDev: boolean): string {
  return [
    "default-src 'self'",
    // 'unsafe-eval' only in development: React uses eval there for debugging, never in production builds.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,
    // Development only: Next's dev overlay applies inline styles without a nonce. Browsers ignore 'unsafe-inline'
    // when a nonce is listed, so development drops the style nonce. Production stays nonce-only.
    isDev ? "style-src 'self' 'unsafe-inline'" : `style-src 'self' 'nonce-${nonce}'`,
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

export const STATIC_SECURITY_HEADERS: ReadonlyArray<{ key: string; value: string }> = [
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  // Browsers ignore HSTS over plain HTTP, so this only takes effect once the app is served over TLS.
  { key: "Strict-Transport-Security", value: "max-age=31536000" },
];
