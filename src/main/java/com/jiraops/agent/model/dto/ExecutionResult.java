package com.jiraops.agent.model.dto;

import com.jiraops.agent.model.enums.JobStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionResult {
    private Long jobId;
    private JobStatus status;
    private int totalIssues;
    private int successCount;
    private int failedCount;
    private LocalDateTime executedAt;
    private String message;
}
