package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.JiraIssueDto;
import com.jiraops.agent.model.dto.NlQueryRequest;
import com.jiraops.agent.model.dto.NlQueryResponse;
import com.jiraops.agent.model.enums.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageService {

    private final GroqService groqService;
    private final JiraApiService jiraApiService;

    private static final int MAX_RESULTS = 100;

    public NlQueryResponse processQuery(NlQueryRequest request) {
        String naturalLanguageQuery = request.getQuery();
        log.info("Processing natural language query: {}", naturalLanguageQuery);

        String generatedJql = groqService.generateJql(naturalLanguageQuery);
        log.info("Generated JQL: {}", generatedJql);

        if (!isValidJql(generatedJql)) {
            log.warn("Invalid JQL generated: {}", generatedJql);
            return NlQueryResponse.builder()
                    .originalQuery(naturalLanguageQuery)
                    .generatedJql(generatedJql)
                    .actionType(ActionType.NATURAL_LANGUAGE)
                    .confidence(0.0)
                    .message("Generated JQL may be invalid. Please review.")
                    .build();
        }

        List<JiraIssueDto> issues = jiraApiService.searchIssues(generatedJql, MAX_RESULTS);
        
        ActionType actionType = determineActionType(naturalLanguageQuery);
        double confidence = calculateConfidence(generatedJql, issues.size());

        return NlQueryResponse.builder()
                .originalQuery(naturalLanguageQuery)
                .generatedJql(generatedJql)
                .actionType(actionType)
                .issues(issues)
                .confidence(confidence)
                .totalIssues(issues.size())
                .message(issues.isEmpty() ? "No issues found matching your query" : "Found " + issues.size() + " issues")
                .build();
    }

    private boolean isValidJql(String jql) {
        if (jql == null || jql.trim().isEmpty()) {
            return false;
        }
        
        if (jql.toLowerCase().contains("error") || jql.toLowerCase().contains("sorry")) {
            return false;
        }

        String[] keywords = {"assignee", "project", "status", "issuetype", "duedate", "summary"};
        boolean hasValidKeyword = false;
        for (String keyword : keywords) {
            if (jql.toLowerCase().contains(keyword)) {
                hasValidKeyword = true;
                break;
            }
        }
        
        return hasValidKeyword;
    }

    private ActionType determineActionType(String query) {
        String lowerQuery = query.toLowerCase();
        
        if (lowerQuery.contains("move") || lowerQuery.contains("change") || 
            lowerQuery.contains("transition") || lowerQuery.contains("set status")) {
            return ActionType.CHANGE_STATUS;
        }
        
        if (lowerQuery.contains("due") || lowerQuery.contains("date") || 
            lowerQuery.contains("shift") || lowerQuery.contains("extend")) {
            return ActionType.UPDATE_DUEDATE;
        }
        
        return ActionType.FETCH;
    }

    private double calculateConfidence(String jql, int issueCount) {
        double confidence = 0.5;
        
        if (jql.contains("assignee = currentUser()")) {
            confidence += 0.2;
        }
        
        if (jql.contains("issuetype")) {
            confidence += 0.1;
        }
        
        if (jql.contains("status")) {
            confidence += 0.1;
        }
        
        if (issueCount > 0) {
            confidence += 0.1;
        }
        
        return Math.min(confidence, 1.0);
    }
}
