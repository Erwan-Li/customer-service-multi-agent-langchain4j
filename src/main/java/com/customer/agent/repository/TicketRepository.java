package com.customer.agent.repository;

import com.customer.agent.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    List<Ticket> findTop20ByOrderByUpdatedAtDesc();

    Optional<Ticket> findTop1ByConversationIdOrderByUpdatedAtDesc(String conversationId);
}
