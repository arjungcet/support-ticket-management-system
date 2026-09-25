import type { NextConfig } from "next";
import { STATIC_SECURITY_HEADERS } from "./src/lib/security/headers";

// /api/* is proxied to the backend at runtime by src/app/api/[...path]/route.ts, which reads BACKEND_URL per
// request. Do not add a rewrite here: rewrites are evaluated at build time and would bake the address into the build.
const nextConfig: NextConfig = {
  // Don't advertise the framework (security review M-2).
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: [...STATIC_SECURITY_HEADERS] }];
  },
};

export default nextConfig;
