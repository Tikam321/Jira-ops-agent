package com.jiraops.agent.model.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraIssueDto {
    private String id;
    private String key;
    private String summary;
    private String status;
    private String assignee;
    private String project;
    private String dueDate;
    private String issueType;
}
