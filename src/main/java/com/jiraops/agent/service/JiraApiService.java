package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.JiraIssueDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@Slf4j
public class JiraApiService {

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.email}")
    private String jiraEmail;

    @Value("${jira.api-token}")
    private String jiraApiToken;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        log.info("Initializing JiraApiService with base URL: {}", jiraBaseUrl);
        webClient = WebClient.builder()
                .baseUrl(jiraBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(
                                (jiraEmail + ":" + jiraApiToken).getBytes()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        log.debug("Jira WebClient initialized");
    }

    public List<JiraIssueDto> searchIssues(String jql, int maxResults) {
        log.info("===========================================");
        log.info("Jira API: Searching issues");
        log.info("JQL: {}", jql);
        log.info("Max Results: {}", maxResults);
        log.info("===========================================");
        
        try {
            long startTime = System.currentTimeMillis();
            
            Map response = webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/rest/api/3/search/jql")
                    .queryParam("jql", jql)
                    .queryParam("maxResults", maxResults)
                    .queryParam("fields", "summary,status,assignee,project,duedate,issuetype")
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Jira API search completed in {} ms", duration);
            
            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
            log.info("Found {} issues from Jira", issues != null ? issues.size() : 0);
            
            List<JiraIssueDto> result = parseIssues(issues);
            log.debug("Parsed {} issues to DTOs", result.size());
            
            return result;
        } catch (Exception e) {
            log.error("Failed to search Jira issues: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search Jira issues: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getTransitions(String issueKey) {
        log.debug("Getting transitions for issue: {}", issueKey);
        
        try {
            Map<String, Object> response = (Map<String, Object>) webClient
                .get()
                .uri("/rest/api/3/issue/" + issueKey + "/transitions")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            List<Map<String, Object>> transitionList = (List<Map<String, Object>>) response.get("transitions");
            List<Map<String, String>> result = new ArrayList<>();
            
            if (transitionList != null) {
                log.debug("Found {} available transitions for {}", transitionList.size(), issueKey);
                for (Map<String, Object> t : transitionList) {
                    Map<String, String> item = new HashMap<>();
                    item.put("id", String.valueOf(t.get("id")));
                    item.put("name", (String) t.get("name"));
                    result.add(item);
                    log.trace("Transition: {} - {}", item.get("id"), item.get("name"));
                }
            } else {
                log.debug("No transitions found for issue: {}", issueKey);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to get transitions for {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to get transitions: " + e.getMessage());
        }
    }

    public boolean updateIssue(String issueKey, Map<String, Object> fields) {
        log.info("Updating issue: {} with fields: {}", issueKey, fields);
        
        try {
            webClient
                .put()
                .uri("/rest/api/3/issue/" + issueKey)
                .bodyValue(Map.of("fields", fields))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            
            log.info("Successfully updated issue: {}", issueKey);
            return true;
        } catch (Exception e) {
            log.error("Failed to update issue {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to update issue: " + e.getMessage());
        }
    }

    public boolean transitionIssue(String issueKey, String transitionId) {
        log.info("Transitioning issue: {} to transition ID: {}", issueKey, transitionId);
        
        try {
            webClient
                .post()
                .uri("/rest/api/3/issue/" + issueKey + "/transitions")
                .bodyValue(Map.of("transition", Map.of("id", transitionId)))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            
            log.info("Successfully transitioned issue: {} to transition: {}", issueKey, transitionId);
            return true;
        } catch (Exception e) {
            log.error("Failed to transition issue {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to transition issue: " + e.getMessage());
        }
    }

    public boolean transitionIssueByStatus(String issueKey, String statusName) {
        log.info("-------------------------------------------");
        log.info("Attempting to transition issue: {} to status: {}", issueKey, statusName);
        
        try {
            List<Map<String, String>> transitions = getTransitions(issueKey);
            
            if (transitions.isEmpty()) {
                log.warn("No available transitions for issue: {}", issueKey);
                return false;
            }
            
            log.debug("Available transitions: {}", transitions);
            
            for (Map<String, String> transition : transitions) {
                if (transition.get("name").equalsIgnoreCase(statusName)) {
                    String transitionId = transition.get("id");
                    log.info("Found matching transition: {} ({})", transition.get("name"), transitionId);
                    return transitionIssue(issueKey, transitionId);
                }
            }
            
            log.warn("Status '{}' not found in available transitions for {}", statusName, issueKey);
            log.debug("Available statuses: {}", transitions.stream()
                    .map(t -> t.get("name"))
                    .toList());
            return false;
        } catch (Exception e) {
            log.error("Failed to transition issue {} to status {}: {}", issueKey, statusName, e.getMessage(), e);
            throw new RuntimeException("Failed to transition issue: " + e.getMessage());
        }
    }

    public boolean addComment(String issueKey, String comment) {
        log.info("-------------------------------------------");
        log.info("Adding comment to issue: {}", issueKey);
        log.info("Comment: {}", comment);
        
        try {
            long startTime = System.currentTimeMillis();
            
            webClient
                .post()
                .uri("/rest/api/3/issue/" + issueKey + "/comment")
                .bodyValue(Map.of("body", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", List.of(Map.of(
                                "type", "paragraph",
                                "content", List.of(Map.of(
                                        "type", "text",
                                        "text", comment
                                ))
                        ))
                )))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully added comment to {} in {} ms", issueKey, duration);
            return true;
        } catch (Exception e) {
            log.error("Failed to add comment to {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to add comment: " + e.getMessage());
        }
    }

    public boolean updateDuedate(String issueKey, String dueDate) {
        log.info("-------------------------------------------");
        log.info("Updating due date for issue: {}", issueKey);
        log.info("New due date: {}", dueDate);
        
        try {
            long startTime = System.currentTimeMillis();
            
            webClient
                .put()
                .uri("/rest/api/3/issue/" + issueKey)
                .bodyValue(Map.of("fields", Map.of("duedate", dueDate)))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully updated due date for {} to {} in {} ms", issueKey, dueDate, duration);
            return true;
        } catch (Exception e) {
            log.error("Failed to update due date for {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to update due date: " + e.getMessage());
        }
    }

    public boolean assignIssue(String issueKey, String assignee) {
        log.info("-------------------------------------------");
        log.info("Assigning issue: {} to: {}", issueKey, assignee);
        
        try {
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> body = new HashMap<>();
            body.put("accountId", assignee);
            
            webClient
                .put()
                .uri("/rest/api/3/issue/" + issueKey + "/assignee")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully assigned {} to {} in {} ms", issueKey, assignee, duration);
            return true;
        } catch (Exception e) {
            log.error("Failed to assign {} to {}: {}", issueKey, assignee, e.getMessage(), e);
            throw new RuntimeException("Failed to assign issue: " + e.getMessage());
        }
    }
    
    public String getCurrentUserAccountId() {
        try {
            Map<String, Object> response = (Map<String, Object>) webClient
                .get()
                .uri("/rest/api/3/myself")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            return (String) response.get("accountId");
        } catch (Exception e) {
            log.error("Failed to get current user accountId: {}", e.getMessage());
            return null;
        }
    }

    private List<JiraIssueDto> parseIssues(List<Map<String, Object>> issues) {
        if (issues == null || issues.isEmpty()) {
            log.debug("No issues to parse");
            return new ArrayList<>();
        }
        
        log.debug("Parsing {} issues", issues.size());
        List<JiraIssueDto> result = new ArrayList<>();
        
        for (Map<String, Object> issue : issues) {
            Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
            
            JiraIssueDto dto = JiraIssueDto.builder()
                .id((String) issue.get("id"))
                .key((String) issue.get("key"))
                .summary((String) fields.get("summary"))
                .issueType(getNestedValue(fields, "issuetype", "name"))
                .status(getNestedValue(fields, "status", "name"))
                .assignee(getNestedValue(fields, "assignee", "displayName"))
                .project(getNestedValue(fields, "project", "key"))
                .dueDate(fields.get("duedate") != null ? fields.get("duedate").toString() : null)
                .build();
            
            log.trace("Parsed issue: {} - {}", dto.getKey(), dto.getSummary());
            result.add(dto);
        }
        
        log.debug("Finished parsing {} issues", result.size());
        return result;
    }

    private String getNestedValue(Map<String, Object> map, String key1, String key2) {
        Object value = map.get(key1);
        if (value instanceof Map) {
            return (String) ((Map<?, ?>) value).get(key2);
        }
        return null;
    }
}
