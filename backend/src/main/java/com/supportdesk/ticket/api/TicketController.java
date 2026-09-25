package com.supportdesk.ticket.api;

import static com.supportdesk.ticket.api.JsonBody.Presence.OPTIONAL;
import static com.supportdesk.ticket.api.JsonBody.Presence.OPTIONAL_CLEARABLE;
import static com.supportdesk.ticket.api.JsonBody.Presence.REQUIRED;
import static com.supportdesk.ticket.api.JsonBody.Presence.REQUIRED_KEY_CLEARABLE;

import com.supportdesk.shared.error.FieldError;
import com.supportdesk.ticket.api.TicketResponses.PageResponse;
import com.supportdesk.ticket.api.TicketResponses.TicketResponse;
import com.supportdesk.ticket.api.TicketResponses.TicketSummaryResponse;
import com.supportdesk.ticket.application.TicketCommands.AssignTicket;
import com.supportdesk.ticket.application.TicketCommands.ChangeStatus;
import com.supportdesk.ticket.application.TicketCommands.CreateTicket;
import com.supportdesk.ticket.application.TicketCommands.UpdateTicket;
import com.supportdesk.ticket.application.TicketService;
import com.supportdesk.ticket.application.TicketViews.TicketView;
import com.supportdesk.ticket.domain.FieldLimits;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import com.supportdesk.ticket.domain.TicketStatus;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket endpoints of spec/api-contract.md §6.1–§6.5, §6.9. Thin by design: parse and validate the request (all
 * field errors together), call one use case, map the result. No business rules here — the state machine lives in the
 * domain (spec/architecture.md §8).
 */
@RestController
@RequestMapping("/api/v1/tickets")
class TicketController {

    private final TicketService tickets;

    TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TicketResponse> create(@RequestBody(required = false) byte[] rawBody) {
        Validation validation = new Validation();
        JsonBody body = JsonBody.parse(rawBody, validation, "title", "description", "priority", "assignee");
        String title = body.text("title", FieldLimits.TITLE, REQUIRED);
        String description = body.text("description", FieldLimits.DESCRIPTION, REQUIRED);
        TicketPriority priority = body.enumValue("priority", TicketPriority.class, OPTIONAL_CLEARABLE);
        String assignee = body.text("assignee", FieldLimits.ASSIGNEE, OPTIONAL_CLEARABLE);
        validation.throwIfInvalid();

        TicketView created = tickets.create(new CreateTicket(
                title, description, priority == null ? TicketPriority.MEDIUM : priority, assignee));
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id()))
                .body(TicketResponse.of(created));
    }

    @GetMapping
    PageResponse<TicketSummaryResponse> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "sort", required = false) String sort) {
        Validation validation = new Validation();
        String keyword = RequestParams.keyword(q, validation);
        Set<TicketStatus> statuses = RequestParams.statuses(status, validation);
        TicketSearchCriteria criteria = RequestParams.criteria(keyword, statuses, sort, validation);
        int pageNumber = RequestParams.page(page, validation);
        int pageSize = RequestParams.size(size, RequestParams.DEFAULT_TICKET_PAGE_SIZE, validation);
        validation.throwIfInvalid();

        return PageResponse.of(tickets.search(criteria, pageNumber, pageSize), TicketSummaryResponse::of);
    }

    @GetMapping("/{ticketId}")
    TicketResponse get(@PathVariable String ticketId) {
        Validation validation = new Validation();
        long id = RequestParams.ticketId(ticketId, validation);
        validation.throwIfInvalid();

        return TicketResponse.of(tickets.get(id));
    }

    @PatchMapping(path = "/{ticketId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    TicketResponse update(@PathVariable String ticketId, @RequestBody(required = false) byte[] rawBody) {
        Validation validation = new Validation();
        JsonBody body = JsonBody.parse(rawBody, validation, "version", "title", "description", "priority");
        long id = RequestParams.ticketId(ticketId, validation);
        Long version = body.version();
        String title = body.text("title", FieldLimits.TITLE, OPTIONAL);
        String description = body.text("description", FieldLimits.DESCRIPTION, OPTIONAL);
        TicketPriority priority = body.enumValue("priority", TicketPriority.class, OPTIONAL);
        if (validation.isEmpty() && !body.hasAny("title", "description", "priority")) {
            validation.add(FieldError.BODY, null, "NO_CHANGES_REQUESTED",
                    "Provide at least one of title, description or priority.");
        }
        validation.throwIfInvalid();

        return TicketResponse.of(tickets.update(new UpdateTicket(id, version, title, description, priority)));
    }

    @PutMapping(path = "/{ticketId}/assignee", consumes = MediaType.APPLICATION_JSON_VALUE)
    TicketResponse assign(@PathVariable String ticketId, @RequestBody(required = false) byte[] rawBody) {
        Validation validation = new Validation();
        JsonBody body = JsonBody.parse(rawBody, validation, "version", "assignee");
        long id = RequestParams.ticketId(ticketId, validation);
        Long version = body.version();
        String assignee = body.text("assignee", FieldLimits.ASSIGNEE, REQUIRED_KEY_CLEARABLE);
        validation.throwIfInvalid();

        return TicketResponse.of(tickets.assign(new AssignTicket(id, version, assignee)));
    }

    @PostMapping(path = "/{ticketId}/status-transitions", consumes = MediaType.APPLICATION_JSON_VALUE)
    TicketResponse changeStatus(@PathVariable String ticketId, @RequestBody(required = false) byte[] rawBody) {
        Validation validation = new Validation();
        JsonBody body = JsonBody.parse(rawBody, validation, "version", "targetStatus");
        long id = RequestParams.ticketId(ticketId, validation);
        Long version = body.version();
        TicketStatus target = body.enumValue("targetStatus", TicketStatus.class, REQUIRED);
        validation.throwIfInvalid();

        return TicketResponse.of(tickets.changeStatus(new ChangeStatus(id, version, target)));
    }
}
