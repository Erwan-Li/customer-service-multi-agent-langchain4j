package com.customer.agent.repository;

import com.customer.agent.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findTop20ByOrderByUpdatedAtDesc();
}
