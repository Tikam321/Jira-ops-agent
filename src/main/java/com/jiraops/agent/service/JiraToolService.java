package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.JiraIssueDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraToolService {

    private final JiraApiService jiraApiService;

    public List<JiraIssueDto> searchIssues(String jql, Integer maxResults, String accessToken) {
        int limit = maxResults != null ? maxResults : 50;
        return jiraApiService.searchIssues(jql, limit, accessToken);
    }

    public String getIssue(String issueKey, String accessToken) {
        List<JiraIssueDto> issues = jiraApiService.searchIssues("key = " + issueKey, 1, accessToken);
        if (issues.isEmpty()) {
            return "Issue not found: " + issueKey;
        }
        JiraIssueDto issue = issues.get(0);
        return issue.getKey() + ": " + issue.getSummary() + "\n" +
            "Status: " + issue.getStatus() + "\n" +
            "Type: " + issue.getIssueType() + "\n" +
            "Assignee: " + issue.getAssignee() + "\n" +
            "Project: " + issue.getProject();
    }

    public String updateIssue(String issueKey, Map<String, Object> fields, String accessToken) {
        if (fields == null || fields.isEmpty()) {
            return "No fields to update";
        }
        boolean result = jiraApiService.updateIssue(issueKey, fields, accessToken);
        return result ? "Successfully updated " + issueKey : "Failed to update " + issueKey;
    }

    public String transitionIssue(String issueKey, String status, String accessToken) {
        boolean result = jiraApiService.transitionIssueByStatus(issueKey, status, accessToken);
        if (result) {
            return "Successfully transitioned " + issueKey + " to " + status;
        }
        List<Map<String, String>> transitions = jiraApiService.getTransitions(issueKey, accessToken);
        String available = String.join(", ", transitions.stream()
            .map(t -> t.get("name"))
            .toList());
        return "Status '" + status + "' not found. Available: " + available;
    }

    public String addComment(String issueKey, String comment, String accessToken) {
        boolean result = jiraApiService.addComment(issueKey, comment, accessToken);
        return result ? "Successfully added comment to " + issueKey : "Failed to add comment";
    }

    public String updateDuedate(String issueKey, String dueDate, String accessToken) {
        boolean result = jiraApiService.updateDuedate(issueKey, dueDate, accessToken);
        return result ? "Updated due date for " + issueKey + " to " + dueDate : "Failed to update due date";
    }

    public String assignIssue(String issueKey, String assignee, String accessToken) {
        // Resolve "me" to account ID
        if (assignee != null && (assignee.toLowerCase().contains("me") || assignee.isEmpty())) {
            String accountId = jiraApiService.getCurrentUserAccountId(accessToken);
            if (accountId != null) {
                assignee = accountId;
            }
        }
        boolean result = jiraApiService.assignIssue(issueKey, assignee, accessToken);
        return result ? "Assigned " + issueKey + " to " + assignee : "Failed to assign issue";
    }

    public String bulkTransition(String jql, String status, String accessToken) {
        List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
        int successCount = 0;
        int failCount = 0;
        List<String> failedIssues = new java.util.ArrayList<>();
        
        for (JiraIssueDto issue : issues) {
            try {
                boolean success = jiraApiService.transitionIssueByStatus(issue.getKey(), status, accessToken);
                if (success) successCount++; 
                else { failCount++; failedIssues.add(issue.getKey()); }
            } catch (Exception e) {
                failCount++;
                failedIssues.add(issue.getKey());
            }
        }
        
        if (failCount == 0) {
            return String.format("Successfully transitioned %d issues to '%s'", successCount, status);
        }
        return String.format("Transitioned %d issues to '%s'. Failed: %d (%s)", 
            successCount, status, failCount, String.join(", ", failedIssues));
    }

    public String bulkUpdateDuedate(String jql, String dueDate, String accessToken) {
        List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
        int successCount = 0;
        int failCount = 0;
        List<String> failedIssues = new java.util.ArrayList<>();
        
        for (JiraIssueDto issue : issues) {
            try {
                boolean success = jiraApiService.updateDuedate(issue.getKey(), dueDate, accessToken);
                if (success) successCount++; 
                else { failCount++; failedIssues.add(issue.getKey()); }
            } catch (Exception e) {
                failCount++;
                failedIssues.add(issue.getKey());
            }
        }
        
        if (failCount == 0) {
            return String.format("Successfully updated due date to %s for %d issues", dueDate, successCount);
        }
        return String.format("Updated due date for %d issues to %s. Failed: %d (%s)", 
            successCount, dueDate, failCount, String.join(", ", failedIssues));
    }

    public String bulkAddComment(String jql, String comment, String accessToken) {
        List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
        int successCount = 0;
        int failCount = 0;
        List<String> failedIssues = new java.util.ArrayList<>();
        
        for (JiraIssueDto issue : issues) {
            try {
                boolean success = jiraApiService.addComment(issue.getKey(), comment, accessToken);
                if (success) successCount++; 
                else { failCount++; failedIssues.add(issue.getKey()); }
            } catch (Exception e) {
                failCount++;
                failedIssues.add(issue.getKey());
            }
        }
        
        if (failCount == 0) {
            return String.format("Successfully added comment to %d issues", successCount);
        }
        return String.format("Added comment to %d issues. Failed: %d (%s)", 
            successCount, failCount, String.join(", ", failedIssues));
    }

    public String bulkAssign(String jql, String assignee, String accessToken) {
        // Resolve "me" to account ID
        if (assignee != null && (assignee.toLowerCase().contains("me") || assignee.isEmpty())) {
            String accountId = jiraApiService.getCurrentUserAccountId(accessToken);
            if (accountId != null) {
                assignee = accountId;
            }
        }
        
        List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
        int successCount = 0;
        int failCount = 0;
        List<String> failedIssues = new java.util.ArrayList<>();
        
        for (JiraIssueDto issue : issues) {
            try {
                boolean success = jiraApiService.assignIssue(issue.getKey(), assignee, accessToken);
                if (success) successCount++; 
                else { failCount++; failedIssues.add(issue.getKey()); }
            } catch (Exception e) {
                failCount++;
                failedIssues.add(issue.getKey());
            }
        }
        
        if (failCount == 0) {
            return String.format("Successfully assigned %d issues to %s", successCount, assignee);
        }
        return String.format("Assigned %d issues to %s. Failed: %d (%s)", 
            successCount, assignee, failCount, String.join(", ", failedIssues));
    }

    public String formatIssues(List<JiraIssueDto> issues) {
        if (issues.isEmpty()) {
            return "No issues found.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(issues.size()).append(" issue(s):\n\n");
        for (JiraIssueDto issue : issues) {
            sb.append("- ").append(issue.getKey()).append(": ").append(issue.getSummary()).append("\n");
            sb.append("  Status: ").append(issue.getStatus()).append("\n");
            sb.append("  Type: ").append(issue.getIssueType()).append("\n");
            if (issue.getDueDate() != null) {
                sb.append("  Due: ").append(issue.getDueDate()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}