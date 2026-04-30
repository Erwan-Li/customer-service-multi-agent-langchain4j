package com.customer.agent.repository;

import com.customer.agent.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTaskRepository extends JpaRepository<AgentTask, String> {

    List<AgentTask> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
