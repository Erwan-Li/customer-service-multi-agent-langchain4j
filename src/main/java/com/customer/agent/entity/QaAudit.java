package com.customer.agent.entity;

import com.customer.agent.enums.QaReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "cs_qa_audit")
public class QaAudit {

    @Id
    private String id;

    @Column(nullable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QaReviewStatus reviewStatus;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String issuesJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String riskFlagsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String approvedReply;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reviewSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (reviewStatus == null) {
            reviewStatus = QaReviewStatus.NOT_REVIEWED;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
