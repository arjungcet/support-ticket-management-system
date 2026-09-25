import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import type { ReactNode } from "react";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "Support Desk",
  description: "Support Ticket Management System",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  // Render per request so Next.js can put the CSP nonce from src/proxy.ts on its scripts.
  await connection();
  return (
    <html lang="en">
      <body>
        <header className="site-header">
          <Link href="/tickets">Support Desk</Link>
          <nav aria-label="Main">
            <Link href="/tickets">Tickets</Link>
            <Link href="/tickets/new">New ticket</Link>
          </nav>
        </header>
        <main>
          <Providers>{children}</Providers>
        </main>
      </body>
    </html>
  );
}
