"use client";

/** Route-level fallback for unexpected rendering errors. Never shows the raw error text. */
export default function RouteError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div role="alert" className="alert">
      <p>Something went wrong while showing this page.</p>
      <button type="button" onClick={reset}>
        Try again
      </button>
    </div>
  );
}
