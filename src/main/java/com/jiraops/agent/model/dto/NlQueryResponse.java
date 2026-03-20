package com.jiraops.agent.model.dto;

import com.jiraops.agent.model.enums.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NlQueryResponse {

    private String originalQuery;
    private String generatedJql;
    private ActionType actionType;
    private List<JiraIssueDto> issues;
    private double confidence;
    private String message;
    private Integer totalIssues;
}
