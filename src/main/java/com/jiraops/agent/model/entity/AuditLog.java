package com.jiraops.agent.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String issueKey;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String action;
    private String actor;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
