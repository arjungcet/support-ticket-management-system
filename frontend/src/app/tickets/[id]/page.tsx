"use client";

import { useParams } from "next/navigation";
import { TicketDetailsView, TicketNotFound } from "@/features/tickets/components/TicketDetailsView";

export default function TicketPage() {
  const { id } = useParams<{ id: string }>();
  const ticketId = /^[1-9]\d*$/.test(id) ? Number(id) : null;
  // Only positive integer ids exist (api-contract §5.1); anything else can't be a ticket.
  if (ticketId === null || !Number.isSafeInteger(ticketId)) {
    return <TicketNotFound />;
  }
  return <TicketDetailsView ticketId={ticketId} />;
}
