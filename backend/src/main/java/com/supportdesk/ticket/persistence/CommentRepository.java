package com.supportdesk.ticket.persistence;

import com.supportdesk.ticket.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Conversation order: oldest first, id as tie-breaker (spec/api-contract.md §6.3a). */
    Page<Comment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId, Pageable pageable);
}
