import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  AddCommentRequest,
  AssignTicketRequest,
  ChangeStatusRequest,
  TicketResponse,
  UpdateTicketRequest,
} from "@/lib/api/types";
import { ticketsApi, type TicketListParams } from "../api";

export const ticketKeys = {
  lists: ["tickets"] as const,
  list: (params: TicketListParams) => ["tickets", params] as const,
  detail: (id: number) => ["ticket", id] as const,
  comments: (id: number) => ["ticket", id, "comments"] as const,
  commentsPage: (id: number, page: number) => ["ticket", id, "comments", page] as const,
};

export function useTickets(params: TicketListParams) {
  return useQuery({
    queryKey: ticketKeys.list(params),
    queryFn: () => ticketsApi.list(params),
    placeholderData: keepPreviousData,
  });
}

export function useTicket(id: number) {
  return useQuery({ queryKey: ticketKeys.detail(id), queryFn: () => ticketsApi.get(id) });
}

export function useComments(id: number, page: number) {
  return useQuery({
    queryKey: ticketKeys.commentsPage(id, page),
    queryFn: () => ticketsApi.listComments(id, page),
    placeholderData: keepPreviousData,
  });
}

/** Every successful ticket mutation returns the full ticket: store it as the latest version and refresh lists. */
function useStoreTicket() {
  const queryClient = useQueryClient();
  return (ticket: TicketResponse) => {
    queryClient.setQueryData(ticketKeys.detail(ticket.id), ticket);
    void queryClient.invalidateQueries({ queryKey: ticketKeys.lists });
  };
}

export function useCreateTicket() {
  const storeTicket = useStoreTicket();
  return useMutation({ mutationFn: ticketsApi.create, onSuccess: storeTicket });
}

export function useUpdateTicket(id: number) {
  const storeTicket = useStoreTicket();
  return useMutation({
    mutationFn: (body: UpdateTicketRequest) => ticketsApi.update(id, body),
    onSuccess: storeTicket,
  });
}

export function useAssignTicket(id: number) {
  const storeTicket = useStoreTicket();
  return useMutation({
    mutationFn: (body: AssignTicketRequest) => ticketsApi.assign(id, body),
    onSuccess: storeTicket,
  });
}

export function useChangeStatus(id: number) {
  const storeTicket = useStoreTicket();
  return useMutation({
    mutationFn: (body: ChangeStatusRequest) => ticketsApi.changeStatus(id, body),
    onSuccess: storeTicket,
  });
}

export function useAddComment(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AddCommentRequest) => ticketsApi.addComment(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ticketKeys.comments(id) }),
  });
}

/** Re-reads the ticket from the server (after a conflict or rejected transition). */
export function useReloadTicket(id: number) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ticketKeys.detail(id) });
}
