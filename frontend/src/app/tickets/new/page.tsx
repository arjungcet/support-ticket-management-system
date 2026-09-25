"use client";

import { useRouter } from "next/navigation";
import { CreateTicketForm } from "@/features/tickets/components/CreateTicketForm";

export default function NewTicketPage() {
  const router = useRouter();
  return (
    <>
      <h1>New ticket</h1>
      <CreateTicketForm onCreated={(ticket) => router.push(`/tickets/${ticket.id}`)} />
    </>
  );
}
