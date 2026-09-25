import type { NextConfig } from "next";

// /api/* is proxied to the backend at runtime by src/app/api/[...path]/route.ts, which reads BACKEND_URL per
// request. Do not add a rewrite here: rewrites are evaluated at build time and would bake the address into the build.
const nextConfig: NextConfig = {};

export default nextConfig;
