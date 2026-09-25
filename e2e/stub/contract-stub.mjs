// Contract stub — an in-memory, file-backed implementation of spec/api-contract.md.
//
// NOT production code and NOT the backend. It exists so the E2E suite can be validated (are the tests themselves
// right?) while the Spring Boot backend does not implement the contract yet. It follows the contract as written;
// where the contract is silent it does the simplest thing and says so in a comment.
// Known simplification: body validation on {ticketId} endpoints runs after the ticket lookup, so the contract's
// "400 before 404" precedence (api-contract §1.3) is not modelled. No E2E journey depends on it.

import { createServer } from "node:http";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";

const PORT = Number(process.env.PORT ?? 18080);
const DATA_FILE = process.env.STUB_DATA_FILE;

const STATUSES = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const ALLOWED = {
  OPEN: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["RESOLVED", "CANCELLED"],
  RESOLVED: ["CLOSED"],
  CLOSED: [],
  CANCELLED: [],
};
const TERMINAL = new Set(["CLOSED", "CANCELLED"]);
const LIMITS = { title: 200, description: 5000, assignee: 100, author: 100, body: 5000, q: 100 };
const SORTS = ["createdAt", "updatedAt", "priority", "status"];
const TITLES = {
  MALFORMED_REQUEST: "Malformed request",
  VALIDATION_FAILED: "Validation failed",
  TICKET_NOT_FOUND: "Ticket not found",
  RESOURCE_NOT_FOUND: "Resource not found",
  METHOD_NOT_ALLOWED: "Method not allowed",
  TICKET_CONCURRENT_MODIFICATION: "Ticket was modified",
  TICKET_INVALID_TRANSITION: "Invalid status transition",
  UNSUPPORTED_MEDIA_TYPE: "Unsupported media type",
  TICKET_NOT_EDITABLE: "Ticket cannot be edited",
  TICKET_NOT_COMMENTABLE: "Ticket cannot be commented on",
};

let db = { nextTicketId: 1, nextCommentId: 1, tickets: {}, comments: [] };
if (DATA_FILE && existsSync(DATA_FILE)) {
  db = JSON.parse(readFileSync(DATA_FILE, "utf8"));
}
const save = () => DATA_FILE && writeFileSync(DATA_FILE, JSON.stringify(db));
const now = () => new Date().toISOString();

class Problem extends Error {
  constructor(status, code, extras = {}) {
    super(code);
    this.status = status;
    this.code = code;
    this.extras = extras;
  }
}

const fieldError = (location, field, code) => ({ location, field, code, message: `${field ?? "request"}: ${code}` });
const invalid = (errors) => new Problem(400, "VALIDATION_FAILED", { errors, detail: `The request contains ${errors.length} invalid field(s).` });

function view(ticket) {
  return { ...ticket, allowedTransitions: ALLOWED[ticket.status] };
}

function checkText(errors, body, field, required) {
  const value = body[field];
  if (value === undefined || value === null) {
    if (required) errors.push(fieldError("body", field, "REQUIRED"));
    return undefined;
  }
  if (typeof value !== "string") throw new Problem(400, "MALFORMED_REQUEST");
  const trimmed = value.trim();
  if (trimmed === "") {
    if (required) errors.push(fieldError("body", field, "BLANK"));
    return required ? undefined : null;
  }
  if ([...trimmed].length > LIMITS[field]) errors.push(fieldError("body", field, "TOO_LONG"));
  return trimmed;
}

function checkUnknown(errors, body, allowed) {
  for (const key of Object.keys(body)) {
    if (!allowed.includes(key)) errors.push(fieldError("body", key, "UNKNOWN_FIELD"));
  }
}

function checkVersion(errors, body) {
  if (body.version === undefined || body.version === null) errors.push(fieldError("body", "version", "REQUIRED"));
  else if (typeof body.version !== "number") throw new Problem(400, "MALFORMED_REQUEST");
  else if (body.version < 0 || !Number.isInteger(body.version)) errors.push(fieldError("body", "version", "INVALID_VALUE"));
}

function findTicket(id) {
  const ticket = db.tickets[id];
  if (!ticket) throw new Problem(404, "TICKET_NOT_FOUND", { ticketId: id, detail: `Ticket ${id} was not found.` });
  return ticket;
}

function requireVersion(ticket, version) {
  if (ticket.version !== version) {
    throw new Problem(409, "TICKET_CONCURRENT_MODIFICATION", {
      ticketId: ticket.id,
      currentVersion: ticket.version,
      detail: `Ticket ${ticket.id} was modified by someone else.`,
    });
  }
}

function requireEditable(ticket) {
  if (TERMINAL.has(ticket.status)) {
    throw new Problem(422, "TICKET_NOT_EDITABLE", { ticketId: ticket.id, currentStatus: ticket.status, detail: `Ticket ${ticket.id} is ${ticket.status}.` });
  }
}

function parseId(raw) {
  if (!/^[1-9]\d{0,15}$/.test(raw)) throw invalid([fieldError("path", "ticketId", "INVALID_VALUE")]);
  return Number(raw);
}

function pageParams(url, defaultSize, errors) {
  const pageRaw = url.searchParams.get("page") ?? "0";
  const sizeRaw = url.searchParams.get("size") ?? String(defaultSize);
  const page = /^\d+$/.test(pageRaw) ? Number(pageRaw) : NaN;
  const size = /^\d+$/.test(sizeRaw) ? Number(sizeRaw) : NaN;
  if (!Number.isInteger(page) || page < 0) errors.push(fieldError("query", "page", "INVALID_VALUE"));
  if (!Number.isInteger(size) || size < 1 || size > 100) errors.push(fieldError("query", "size", "INVALID_VALUE"));
  return { page, size };
}

function paginate(items, page, size) {
  const totalElements = items.length;
  return {
    content: items.slice(page * size, page * size + size),
    page: { number: page, size, totalElements, totalPages: totalElements === 0 ? 0 : Math.ceil(totalElements / size) },
  };
}

const rank = {
  priority: (t) => PRIORITIES.indexOf(t.priority),
  status: (t) => STATUSES.indexOf(t.status),
  createdAt: (t) => t.createdAt,
  updatedAt: (t) => t.updatedAt,
};

function listTickets(url) {
  const errors = [];
  const q = (url.searchParams.get("q") ?? "").trim();
  if ([...q].length > LIMITS.q) errors.push(fieldError("query", "q", "TOO_LONG"));
  const statuses = url.searchParams.getAll("status").flatMap((s) => s.split(","));
  if (statuses.some((s) => !STATUSES.includes(s))) errors.push(fieldError("query", "status", "INVALID_VALUE"));
  const [sortField, sortDir = "asc"] = (url.searchParams.get("sort") ?? "createdAt,desc").split(",");
  if (!SORTS.includes(sortField) || !["asc", "desc"].includes(sortDir)) errors.push(fieldError("query", "sort", "INVALID_VALUE"));
  const { page, size } = pageParams(url, 20, errors);
  if (errors.length) throw invalid(errors);

  const needle = q.toLowerCase();
  const direction = sortDir === "desc" ? -1 : 1;
  const matches = Object.values(db.tickets)
    .filter((t) => !needle || t.title.toLowerCase().includes(needle) || t.description.toLowerCase().includes(needle))
    .filter((t) => statuses.length === 0 || statuses.includes(t.status))
    .sort((a, b) => {
      const [x, y] = [rank[sortField](a), rank[sortField](b)];
      return (x < y ? -1 : x > y ? 1 : a.id - b.id) * direction;
    })
    .map(({ id, title, priority, status, assignee, createdAt, updatedAt }) => ({ id, title, priority, status, assignee, createdAt, updatedAt }));
  return paginate(matches, page, size);
}

function createTicket(body) {
  const errors = [];
  checkUnknown(errors, body, ["title", "description", "priority", "assignee"]);
  const title = checkText(errors, body, "title", true);
  const description = checkText(errors, body, "description", true);
  const assignee = checkText(errors, body, "assignee", false) ?? null;
  const priority = body.priority ?? "MEDIUM";
  if (!PRIORITIES.includes(priority)) errors.push(fieldError("body", "priority", "INVALID_VALUE"));
  if (errors.length) throw invalid(errors);
  const at = now();
  const ticket = {
    id: db.nextTicketId++, title, description, priority, status: "OPEN", assignee,
    createdAt: at, updatedAt: at, resolvedAt: null, closedAt: null, cancelledAt: null, version: 0,
  };
  db.tickets[ticket.id] = ticket;
  return ticket;
}

function updateTicket(ticket, body) {
  const errors = [];
  checkUnknown(errors, body, ["version", "title", "description", "priority"]);
  checkVersion(errors, body);
  const changes = {};
  for (const field of ["title", "description"]) {
    if (field in body) {
      const value = checkText(errors, body, field, true);
      if (value !== undefined) changes[field] = value;
    }
  }
  if ("priority" in body) {
    if (body.priority === null) errors.push(fieldError("body", "priority", "REQUIRED"));
    else if (!PRIORITIES.includes(body.priority)) errors.push(fieldError("body", "priority", "INVALID_VALUE"));
    else changes.priority = body.priority;
  }
  if (!["title", "description", "priority"].some((f) => f in body)) errors.push(fieldError("body", null, "NO_CHANGES_REQUESTED"));
  if (errors.length) throw invalid(errors);
  requireVersion(ticket, body.version);
  requireEditable(ticket);
  const changed = Object.entries(changes).filter(([k, v]) => ticket[k] !== v);
  if (changed.length) {
    changed.forEach(([k, v]) => (ticket[k] = v));
    ticket.updatedAt = now();
    ticket.version += 1;
  }
  return ticket;
}

function assignTicket(ticket, body) {
  const errors = [];
  checkUnknown(errors, body, ["version", "assignee"]);
  checkVersion(errors, body);
  if (!("assignee" in body)) errors.push(fieldError("body", "assignee", "REQUIRED"));
  const assignee = "assignee" in body ? checkText(errors, body, "assignee", false) ?? null : null;
  if (errors.length) throw invalid(errors);
  requireVersion(ticket, body.version);
  requireEditable(ticket);
  if (ticket.assignee !== assignee) {
    ticket.assignee = assignee;
    ticket.updatedAt = now();
    ticket.version += 1;
  }
  return ticket;
}

function changeStatus(ticket, body) {
  const errors = [];
  checkUnknown(errors, body, ["version", "targetStatus"]);
  checkVersion(errors, body);
  if (body.targetStatus === undefined || body.targetStatus === null) errors.push(fieldError("body", "targetStatus", "REQUIRED"));
  else if (!STATUSES.includes(body.targetStatus)) errors.push(fieldError("body", "targetStatus", "INVALID_VALUE"));
  if (errors.length) throw invalid(errors);
  requireVersion(ticket, body.version);
  const target = body.targetStatus;
  if (!ALLOWED[ticket.status].includes(target)) {
    throw new Problem(409, "TICKET_INVALID_TRANSITION", {
      ticketId: ticket.id, currentStatus: ticket.status, targetStatus: target, allowedTransitions: ALLOWED[ticket.status],
      detail: `Ticket ${ticket.id} cannot move from ${ticket.status} to ${target}.`,
    });
  }
  const at = now();
  ticket.status = target;
  ticket.updatedAt = at;
  if (target === "RESOLVED") ticket.resolvedAt = at;
  if (target === "CLOSED") ticket.closedAt = at;
  if (target === "CANCELLED") ticket.cancelledAt = at;
  ticket.version += 1;
  return ticket;
}

function addComment(ticket, body) {
  const errors = [];
  checkUnknown(errors, body, ["author", "body"]);
  const author = checkText(errors, body, "author", true);
  const text = checkText(errors, body, "body", true);
  if (errors.length) throw invalid(errors);
  if (TERMINAL.has(ticket.status)) {
    throw new Problem(422, "TICKET_NOT_COMMENTABLE", { ticketId: ticket.id, currentStatus: ticket.status, detail: `Ticket ${ticket.id} is ${ticket.status}.` });
  }
  const comment = { id: db.nextCommentId++, ticketId: ticket.id, author, body: text, createdAt: now() };
  db.comments.push(comment);
  return comment;
}

function listComments(ticket, url) {
  const errors = [];
  const { page, size } = pageParams(url, 50, errors);
  if (errors.length) throw invalid(errors);
  return paginate(db.comments.filter((c) => c.ticketId === ticket.id), page, size);
}

async function readJson(req) {
  if (!(req.headers["content-type"] ?? "").startsWith("application/json")) {
    throw new Problem(415, "UNSUPPORTED_MEDIA_TYPE");
  }
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  try {
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (typeof body !== "object" || body === null || Array.isArray(body)) throw new Error("not an object");
    return body;
  } catch {
    throw new Problem(400, "MALFORMED_REQUEST");
  }
}

async function route(req, url) {
  const parts = url.pathname.replace(/\/+$/, "").split("/").slice(1); // api, v1, tickets, {id}, sub
  if (parts[0] !== "api" || parts[1] !== "v1" || parts[2] !== "tickets" || parts.length > 5) {
    throw new Problem(404, "RESOURCE_NOT_FOUND");
  }
  const method = req.method;
  if (parts.length === 3) {
    if (method === "GET") return [200, listTickets(url)];
    if (method === "POST") {
      const ticket = createTicket(await readJson(req));
      return [201, view(ticket), { Location: `/api/v1/tickets/${ticket.id}` }];
    }
    throw new Problem(405, "METHOD_NOT_ALLOWED", { allow: "GET, POST" });
  }
  const sub = parts[4];
  const allowed = { undefined: ["GET", "PATCH"], assignee: ["PUT"], "status-transitions": ["POST"], comments: ["GET", "POST"] }[sub];
  if (!allowed) throw new Problem(404, "RESOURCE_NOT_FOUND");
  if (!allowed.includes(method)) throw new Problem(405, "METHOD_NOT_ALLOWED", { allow: allowed.join(", ") });
  const id = parseId(parts[3]);
  const body = ["POST", "PATCH", "PUT"].includes(method) ? await readJson(req) : undefined;
  const ticket = findTicket(id);
  if (sub === undefined && method === "GET") return [200, view(ticket)];
  if (sub === undefined) return [200, view(updateTicket(ticket, body))];
  if (sub === "assignee") return [200, view(assignTicket(ticket, body))];
  if (sub === "status-transitions") return [200, view(changeStatus(ticket, body))];
  if (method === "GET") return [200, listComments(ticket, url)];
  const comment = addComment(ticket, body);
  return [201, comment, { Location: `/api/v1/tickets/${id}/comments/${comment.id}` }];
}

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const incoming = req.headers["x-correlation-id"];
  const correlationId = typeof incoming === "string" && /^[A-Za-z0-9-]{1,64}$/.test(incoming) ? incoming : randomUUID();
  try {
    const [status, payload, headers = {}] = await route(req, url);
    save();
    res.writeHead(status, { "Content-Type": "application/json", "X-Correlation-Id": correlationId, ...headers });
    res.end(JSON.stringify(payload));
  } catch (error) {
    const problem = error instanceof Problem ? error : new Problem(500, "INTERNAL_ERROR");
    const { errors = [], detail, allow, ...extensions } = problem.extras;
    const body = {
      type: `https://supportdesk.example/problems/${problem.code.toLowerCase().replaceAll("_", "-")}`,
      title: TITLES[problem.code] ?? "Internal server error",
      status: problem.status,
      detail: detail ?? (problem.status === 500 ? "An unexpected error occurred. Please try again or contact support with the correlation id." : TITLES[problem.code]),
      instance: url.pathname,
      code: problem.code,
      correlationId,
      timestamp: now(),
      errors,
      ...extensions,
    };
    if (problem.status === 500) console.error(error);
    res.writeHead(problem.status, {
      "Content-Type": "application/problem+json",
      "X-Correlation-Id": correlationId,
      ...(allow ? { Allow: allow } : {}),
    });
    res.end(JSON.stringify(body));
  }
}).listen(PORT, () => console.log(`contract stub listening on ${PORT}`));
