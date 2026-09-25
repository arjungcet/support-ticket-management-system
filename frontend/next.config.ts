import type { NextConfig } from "next";

// The browser only talks to this origin; /api/* is proxied to the backend (spec/architecture.md §2).
// BACKEND_URL is server-side only — never exposed via NEXT_PUBLIC_*.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
