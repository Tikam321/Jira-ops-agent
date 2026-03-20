package com.jiraops.agent.model.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewResult {
    private String jql;
    private int totalIssues;
    private java.util.List<JiraIssueDto> issues;
    private java.util.List<PreviewChange> changes;
}
