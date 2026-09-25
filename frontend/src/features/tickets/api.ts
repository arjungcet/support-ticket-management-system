import { apiRequest } from "@/lib/api/client";
import {
  DEFAULT_COMMENT_PAGE_SIZE,
  type AddCommentRequest,
  type AssignTicketRequest,
  type ChangeStatusRequest,
  type CommentResponse,
  type CreateTicketRequest,
  type PageResponse,
  type TicketResponse,
  type TicketStatus,
  type TicketSummaryResponse,
  type UpdateTicketRequest,
} from "@/lib/api/types";

/** Query parameters of GET /tickets (spec/api-contract.md §6.2). */
export interface TicketListParams {
  q: string;
  status: TicketStatus[];
  page: number;
  size: number;
  sort: string;
}

export function ticketListQuery(params: TicketListParams): string {
  const search = new URLSearchParams();
  const keyword = params.q.trim();
  if (keyword) {
    search.set("q", keyword);
  }
  params.status.forEach((status) => search.append("status", status));
  search.set("page", String(params.page));
  search.set("size", String(params.size));
  search.set("sort", params.sort);
  return search.toString();
}

export const ticketsApi = {
  list: (params: TicketListParams) =>
    apiRequest<PageResponse<TicketSummaryResponse>>("GET", `/tickets?${ticketListQuery(params)}`),
  get: (id: number) => apiRequest<TicketResponse>("GET", `/tickets/${id}`),
  create: (body: CreateTicketRequest) => apiRequest<TicketResponse>("POST", "/tickets", body),
  update: (id: number, body: UpdateTicketRequest) => apiRequest<TicketResponse>("PATCH", `/tickets/${id}`, body),
  assign: (id: number, body: AssignTicketRequest) =>
    apiRequest<TicketResponse>("PUT", `/tickets/${id}/assignee`, body),
  changeStatus: (id: number, body: ChangeStatusRequest) =>
    apiRequest<TicketResponse>("POST", `/tickets/${id}/status-transitions`, body),
  listComments: (id: number, page: number, size = DEFAULT_COMMENT_PAGE_SIZE) =>
    apiRequest<PageResponse<CommentResponse>>("GET", `/tickets/${id}/comments?page=${page}&size=${size}`),
  addComment: (id: number, body: AddCommentRequest) =>
    apiRequest<CommentResponse>("POST", `/tickets/${id}/comments`, body),
};
