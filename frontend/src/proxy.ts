import { NextResponse, type NextRequest } from "next/server";
import { contentSecurityPolicy } from "@/lib/security/headers";

/**
 * Sets a nonce-based Content-Security-Policy on every page (security review M-2). Next.js reads the nonce from the
 * request's CSP header while rendering and adds it to its own scripts, so pages must render per request
 * (see `connection()` in app/layout.tsx).
 */
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const policy = contentSecurityPolicy(nonce, process.env.NODE_ENV === "development");

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("Content-Security-Policy", policy);
  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: [
    // Pages only: not the /api proxy (JSON), static assets, or link prefetches.
    {
      source: "/((?!api|_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
