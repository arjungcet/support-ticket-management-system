/**
 * Same-origin proxy for the backend API (spec/architecture.md §2): the browser only talks to this origin.
 *
 * BACKEND_URL is read on every request, so one build can run in any environment (architecture §17). A build-time
 * rewrite in next.config.ts would bake the address into the build output instead (review M-3 / E2E finding I-3).
 * Only API headers are forwarded — cookies and other browser headers never reach the backend, and backend cookies
 * never reach the browser.
 */

export const dynamic = "force-dynamic";

const REQUEST_HEADERS = ["accept", "content-type", "x-correlation-id"];
const RESPONSE_HEADERS = ["content-type", "location", "x-correlation-id", "allow"];
const DEVELOPMENT_BACKEND = "http://localhost:8080";

function backendBaseUrl(): string | null {
  const configured = process.env.BACKEND_URL?.trim();
  if (configured) {
    return configured.replace(/\/+$/, "");
  }
  return process.env.NODE_ENV === "development" ? DEVELOPMENT_BACKEND : null;
}

async function proxy(request: Request, context: { params: Promise<{ path: string[] }> }): Promise<Response> {
  const base = backendBaseUrl();
  if (base === null) {
    console.error("BACKEND_URL is not configured; the /api proxy cannot reach the backend.");
    return new Response("Service misconfigured", { status: 500 });
  }

  const { path } = await context.params;
  const target = `${base}/api/${path.map(encodeURIComponent).join("/")}${new URL(request.url).search}`;
  const headers = new Headers();
  for (const name of REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value !== null) {
      headers.set(name, value);
    }
  }

  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
      redirect: "manual",
      cache: "no-store",
    });
  } catch {
    return new Response("Bad Gateway", { status: 502 });
  }

  const responseHeaders = new Headers();
  for (const name of RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value !== null) {
      responseHeaders.set(name, value);
    }
  }
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
