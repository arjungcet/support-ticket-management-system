"use client";

import { Suspense } from "react";
import { TicketListView } from "@/features/tickets/components/TicketListView";
import { useTicketListParams } from "@/features/tickets/hooks/useTicketListParams";

function TicketsPageContent() {
  const [params, setParams] = useTicketListParams();
  return <TicketListView params={params} onParamsChange={setParams} />;
}

export default function TicketsPage() {
  return (
    <>
      <h1>Tickets</h1>
      <Suspense fallback={<p>Loading tickets…</p>}>
        <TicketsPageContent />
      </Suspense>
    </>
  );
}
