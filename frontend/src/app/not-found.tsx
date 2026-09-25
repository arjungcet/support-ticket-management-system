import Link from "next/link";

export default function NotFound() {
  return (
    <section>
      <h1>Page not found</h1>
      <Link href="/tickets">Go to tickets</Link>
    </section>
  );
}
