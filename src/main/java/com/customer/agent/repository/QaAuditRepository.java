package com.customer.agent.repository;

import com.customer.agent.entity.QaAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QaAuditRepository extends JpaRepository<QaAudit, String> {

    Optional<QaAudit> findTop1ByConversationIdOrderByUpdatedAtDesc(String conversationId);
}
