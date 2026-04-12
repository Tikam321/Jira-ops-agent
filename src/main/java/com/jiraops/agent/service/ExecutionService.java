package com.jiraops.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiraops.agent.model.dto.*;
import com.jiraops.agent.model.entity.AuditLog;
import com.jiraops.agent.model.entity.ExecutionJob;
import com.jiraops.agent.model.enums.ActionType;
import com.jiraops.agent.model.enums.JobStatus;
import com.jiraops.agent.repository.AuditLogRepository;
import com.jiraops.agent.repository.ExecutionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final JiraApiService jiraApiService;
    private final CommandTemplateService commandTemplateService;
    private final ExecutionJobRepository jobRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RESULTS = 100;

    public PreviewResult preview(String commandId, String accessToken) {
        CommandTemplate command = commandTemplateService.getCommandById(commandId);
        if (command == null) {
            throw new IllegalArgumentException("Command not found: " + commandId);
        }

        List<JiraIssueDto> issues = jiraApiService.searchIssues(command.getJql(), MAX_RESULTS, accessToken);
        
        List<PreviewChange> changes = generatePreviewChanges(command, issues);
        
        ExecutionJob job = ExecutionJob.builder()
            .commandId(commandId)
            .jql(command.getJql())
            .actionType(command.getActionType().name())
            .totalIssues(issues.size())
            .status(JobStatus.PREVIEW.name())
            .createdAt(LocalDateTime.now())
            .build();
        jobRepository.save(job);

        return PreviewResult.builder()
            .jql(command.getJql())
            .totalIssues(issues.size())
            .issues(issues)
            .changes(changes)
            .build();
    }

    public ExecutionResult execute(String commandId, String accessToken) {
        CommandTemplate command = commandTemplateService.getCommandById(commandId);
        if (command == null) {
            throw new IllegalArgumentException("Command not found: " + commandId);
        }

        List<JiraIssueDto> issues = jiraApiService.searchIssues(command.getJql(), MAX_RESULTS, accessToken);
        
        ExecutionJob job = ExecutionJob.builder()
            .commandId(commandId)
            .jql(command.getJql())
            .actionType(command.getActionType().name())
            .totalIssues(issues.size())
            .status(JobStatus.RUNNING.name())
            .createdAt(LocalDateTime.now())
            .build();
        job = jobRepository.save(job);

        int successCount = 0;
        int failedCount = 0;

        for (JiraIssueDto issue : issues) {
            try {
                executeAction(command, issue, job.getId(), accessToken);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                logAudit(issue.getKey(), "action", "N/A", "FAILED", command.getActionType().name(), job.getId());
            }
        }

        job.setStatus(failedCount == 0 ? JobStatus.COMPLETED.name() : JobStatus.FAILED.name());
        job.setSuccessCount(successCount);
        job.setFailedCount(failedCount);
        job.setExecutedAt(LocalDateTime.now());
        jobRepository.save(job);

        return ExecutionResult.builder()
            .jobId(job.getId())
            .status(JobStatus.valueOf(job.getStatus()))
            .totalIssues(issues.size())
            .successCount(successCount)
            .failedCount(failedCount)
            .executedAt(job.getExecutedAt())
            .message(failedCount == 0 ? "All issues updated successfully" : "Completed with " + failedCount + " failures")
            .build();
    }

    private List<PreviewChange> generatePreviewChanges(CommandTemplate command, List<JiraIssueDto> issues) {
        List<PreviewChange> changes = new ArrayList<>();
        
        for (JiraIssueDto issue : issues) {
            if (command.getActionType() == ActionType.UPDATE_DUEDATE) {
                String newDate = calculateNewDueDate(issue.getDueDate(), "+1M");
                changes.add(PreviewChange.builder()
                    .issueKey(issue.getKey())
                    .field("duedate")
                    .currentValue(issue.getDueDate())
                    .newValue(newDate)
                    .build());

            } else if (command.getActionType() == ActionType.CHANGE_STATUS) {
                try {
                    Map<String, Object> params = objectMapper.readValue(command.getParameters().toString(), Map.class);
                    String newStatus = (String) params.get("toStatus");
                    changes.add(PreviewChange.builder()
                        .issueKey(issue.getKey())
                        .field("status")
                        .currentValue(issue.getStatus())
                        .newValue(newStatus)
                        .build());
                } catch (Exception e) {
                    // ignore parsing errors
                }
            }
        }
        return changes;
    }

    private void executeAction(CommandTemplate command, JiraIssueDto issue, Long jobId, String accessToken) {
        if (command.getActionType() == ActionType.UPDATE_DUEDATE) {
            String newDate = calculateNewDueDate(issue.getDueDate(), "+1M");
            jiraApiService.updateIssue(issue.getKey(), Map.of("duedate", newDate), accessToken);
            logAudit(issue.getKey(), "duedate", issue.getDueDate(), newDate, "UPDATE_DUEDATE", jobId);
            
        } else if (command.getActionType() == ActionType.CHANGE_STATUS) {
            List<Map<String, String>> transitions = jiraApiService.getTransitions(issue.getKey(), accessToken);
            Map<String, Object> params = null;
            try {
                params = objectMapper.readValue(command.getParameters().toString(), Map.class);
            } catch (Exception e) {}
            
            if (params != null) {
                String toStatus = (String) params.get("toStatus");
                String transitionId = transitions.stream()
                    .filter(t -> t.get("name").equals(toStatus))
                    .findFirst()
                    .map(t -> t.get("id"))
                    .orElse(null);
                
                if (transitionId != null) {
                    jiraApiService.transitionIssue(issue.getKey(), transitionId, accessToken);
                    logAudit(issue.getKey(), "status", issue.getStatus(), toStatus, "CHANGE_STATUS", jobId);
                }
            }
        }
    }

    private String calculateNewDueDate(String currentDate, String delta) {
        if (currentDate == null) return null;
        try {
            LocalDateTime date = LocalDateTime.parse(currentDate + "T00:00:00");
            if (delta.equals("+1M")) {
                date = date.plusMonths(1);
            }
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private void logAudit(String issueKey, String field, String oldValue, String newValue, String action, Long jobId) {
        AuditLog log = AuditLog.builder()
            .issueKey(issueKey)
            .fieldName(field)
            .oldValue(oldValue)
            .newValue(newValue)
            .action(action)
            .actor("system")
            .jobId(jobId)
            .executedAt(LocalDateTime.now())
            .build();
        auditLogRepository.save(log);
    }

    public List<ExecutionJob> getExecutionHistory() {
        return jobRepository.findAll();
    }
}