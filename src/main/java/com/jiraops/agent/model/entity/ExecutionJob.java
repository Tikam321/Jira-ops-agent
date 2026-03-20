package com.jiraops.agent.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "execution_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String commandId;
    private String jql;
    private String actionType;
    private Integer totalIssues;
    private Integer successCount;
    private Integer failedCount;
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    private String errorMessage;
}
