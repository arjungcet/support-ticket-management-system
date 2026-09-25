package com.supportdesk.ticket.api;

import static com.supportdesk.ticket.api.JsonBody.Presence.REQUIRED;

import com.supportdesk.ticket.api.TicketResponses.CommentResponse;
import com.supportdesk.ticket.api.TicketResponses.PageResponse;
import com.supportdesk.ticket.application.TicketCommands.AddComment;
import com.supportdesk.ticket.application.TicketCommentService;
import com.supportdesk.ticket.application.TicketViews.CommentView;
import com.supportdesk.ticket.domain.FieldLimits;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Comment endpoints of spec/api-contract.md §6.6 and §6.3a. */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
class TicketCommentController {

    private final TicketCommentService comments;

    TicketCommentController(TicketCommentService comments) {
        this.comments = comments;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CommentResponse> add(@PathVariable String ticketId, @RequestBody(required = false) byte[] rawBody) {
        Validation validation = new Validation();
        JsonBody body = JsonBody.parse(rawBody, validation, "author", "body");
        long id = RequestParams.ticketId(ticketId, validation);
        String author = body.text("author", FieldLimits.COMMENT_AUTHOR, REQUIRED);
        String text = body.multiLineText("body", FieldLimits.COMMENT_BODY, REQUIRED);
        validation.throwIfInvalid();

        CommentView comment = comments.add(new AddComment(id, author, text));
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + id + "/comments/" + comment.id()))
                .body(CommentResponse.of(comment));
    }

    @GetMapping
    PageResponse<CommentResponse> list(@PathVariable String ticketId,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        Validation validation = new Validation();
        long id = RequestParams.ticketId(ticketId, validation);
        int pageNumber = RequestParams.page(page, validation);
        int pageSize = RequestParams.size(size, RequestParams.DEFAULT_COMMENT_PAGE_SIZE, validation);
        validation.throwIfInvalid();

        return PageResponse.of(comments.list(id, pageNumber, pageSize), CommentResponse::of);
    }
}
