package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.JiraIssueDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@Slf4j
public class JiraApiService {

    private static final String ATLASSIAN_API_BASE = "https://api.atlassian.com";

//    @Value("${jira.base-url}")
//    private String jiraBaseUrl;

    public List<JiraIssueDto> searchIssues(String jql, int maxResults, String accessToken) {
        log.info("===========================================");
        log.info("Jira API: Searching issues");
        log.info("JQL: {}", jql);
        log.info("Max Results: {}", maxResults);
        log.info("===========================================");
        
        try {
            long startTime = System.currentTimeMillis();
            
            if (accessToken == null || accessToken.isEmpty()) {
                throw new RuntimeException("Access token is null or empty");
            }
            log.debug("Using access token (first 20 chars): {}", accessToken.substring(0, Math.min(20, accessToken.length())));
            
            // Get accessible resources to find the correct site ID
            WebClient accessClient = WebClient.builder()
                .baseUrl(ATLASSIAN_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
            
            var typeRef = new ParameterizedTypeReference<List<Map<String, Object>>>() {};
            List<Map<String, Object>> resources = accessClient.get()
                .uri("/oauth/token/accessible-resources")
                .retrieve()
                .bodyToMono(typeRef)
                .block();
            
            if (resources == null || resources.isEmpty()) {
                throw new RuntimeException("No accessible resources found");
            }
            
            // Get the first resource - use 'id' (site ID/UUID) 
            Map<String, Object> firstResource = resources.get(0);
            
            // Log all keys to debug what we have
            log.info("Resource keys: {}", firstResource.keySet());
            log.info("Resource data: {}", firstResource);
            
            // Use 'id' field which is the cloud ID (UUID format)
            String siteId = (String) firstResource.get("id");
            String siteName = (String) firstResource.get("name");
            String siteUrl = (String) firstResource.get("url");
            List<String> scopes = (List<String>) firstResource.get("scopes");
            log.info("Using site ID: {} (site: {} - {})", siteId, siteName, siteUrl);
            log.info("Available scopes: {}", scopes);
            
            // Use proper API format: /ex/jira/{cloudId}/rest/api/3
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            log.info("API URL: {}", apiUrl);
            
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            log.info("Making request with jql: {}", jql);
            
            // Request body for POST
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jql", jql);
            requestBody.put("maxResults", maxResults);
            requestBody.put("fields", List.of("summary", "status", "assignee", "project", "duedate", "issuetype"));
            
            log.debug("Request body: {}", requestBody);
            
            Map searchResult;
            try {
                searchResult = webClient
                    .post()
                    .uri("/search/jql")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
                
                log.info("Search successful with POST /search/jql!");
            } catch (Exception e) {
                log.error("POST /search/jql failed: {}. Trying GET /search...", e.getMessage());
                
                try {
                    searchResult = webClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("jql", jql)
                            .queryParam("maxResults", maxResults)
                            .queryParam("fields", "summary,status,assignee,project,duedate,issuetype")
                            .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                    log.info("Search successful with GET /search!");
                } catch (Exception e2) {
                    log.error("Both POST and GET failed: {}", e2.getMessage());
                    throw new RuntimeException("Failed to search Jira issues: " + e2.getMessage());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Jira API search completed in {} ms", duration);
            
            List<Map<String, Object>> issues = (List<Map<String, Object>>) searchResult.get("issues");
            log.info("Found {} issues from Jira", issues != null ? issues.size() : 0);
            
            List<JiraIssueDto> dtoResult = parseIssues(issues);
            log.debug("Parsed {} issues to DTOs", dtoResult.size());
            
            return dtoResult;
        } catch (Exception e) {
            log.error("Failed to search Jira issues: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search Jira issues: " + e.getMessage());
        }
    }

    private String getJiraDomain(String accessToken) {
        try {
            WebClient webClient = WebClient.builder()
                .baseUrl(ATLASSIAN_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
            
            var typeRef = new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {};
            List<Map<String, Object>> resources = webClient.get()
                .uri("/oauth/token/accessible-resources")
                .retrieve()
                .bodyToMono(typeRef)
                .block();
            
            log.debug("Accessible resources: {}", resources);
            
            if (resources != null && !resources.isEmpty()) {
                Map<String, Object> firstResource = resources.get(0);
                
                // Log all keys to debug what we have
                log.info("Resource keys: {}", firstResource.keySet());
                log.info("Resource data: {}", firstResource);
                
                // Use 'id' field which is the cloud ID (UUID format)
                String id = (String) firstResource.get("id");
                log.info("Found site ID: {}", id);
                return id;
            }
            
            throw new RuntimeException("No accessible resources found");
        } catch (Exception e) {
            log.error("Failed to get site ID: {}", e.getMessage());
            throw new RuntimeException("Failed to get site ID: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getTransitions(String issueKey, String accessToken) {
        log.debug("Getting transitions for issue: {}", issueKey);
        
        try {
            String siteId = getJiraDomain(accessToken);
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            Map<String, Object> response = (Map<String, Object>) webClient
                .get()
                .uri("/issue/" + issueKey + "/transitions")
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

    public boolean updateIssue(String issueKey, Map<String, Object> fields, String accessToken) {
        log.info("Updating issue: {} with fields: {}", issueKey, fields);
        
        try {
            String siteId = getJiraDomain(accessToken);
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            webClient
                .put()
                .uri("/issue/" + issueKey)
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

    public boolean transitionIssue(String issueKey, String transitionId, String accessToken) {
        log.info("Transitioning issue: {} to transition ID: {}", issueKey, transitionId);
        
        try {
            String siteId = getJiraDomain(accessToken);
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            webClient
                .post()
                .uri("/issue/" + issueKey + "/transitions")
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

    public boolean transitionIssueByStatus(String issueKey, String statusName, String accessToken) {
        log.info("-------------------------------------------");
        log.info("Attempting to transition issue: {} to status: {}", issueKey, statusName);
        
        try {
            List<Map<String, String>> transitions = getTransitions(issueKey, accessToken);
            
            if (transitions.isEmpty()) {
                log.warn("No available transitions for issue: {}", issueKey);
                return false;
            }
            
            log.debug("Available transitions: {}", transitions);
            
            for (Map<String, String> transition : transitions) {
                if (transition.get("name").equalsIgnoreCase(statusName)) {
                    String transitionId = transition.get("id");
                    log.info("Found matching transition: {} ({})", transition.get("name"), transitionId);
                    return transitionIssue(issueKey, transitionId, accessToken);
                }
            }
            
            log.warn("Status '{}' not found in available transitions for {}", statusName, issueKey);
            return false;
        } catch (Exception e) {
            log.error("Failed to transition issue {} to status {}: {}", issueKey, statusName, e.getMessage(), e);
            throw new RuntimeException("Failed to transition issue: " + e.getMessage());
        }
    }

    public boolean addComment(String issueKey, String comment, String accessToken) {
        log.info("-------------------------------------------");
        log.info("Adding comment to issue: {}", issueKey);
        log.info("Comment: {}", comment);
        
        try {
            long startTime = System.currentTimeMillis();
            
            String siteId = getJiraDomain(accessToken);
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            webClient
                .post()
                .uri("/issue/" + issueKey + "/comment")
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

    public boolean updateDuedate(String issueKey, String dueDate, String accessToken) {
        log.info("-------------------------------------------");
        log.info("Updating due date for issue: {}", issueKey);
        log.info("New due date: {}", dueDate);
        
        try {
            long startTime = System.currentTimeMillis();
            
            String siteId = getJiraDomain(accessToken);
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            webClient
                .put()
                .uri("/issue/" + issueKey)
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

    public boolean assignIssue(String issueKey, String assignee, String accessToken) {
        log.info("-------------------------------------------");
        log.info("Assigning issue: {} to: {}", issueKey, assignee);
        
        try {
            long startTime = System.currentTimeMillis();
            
            String siteId = getJiraDomain(accessToken);
            String apiUrl = ATLASSIAN_API_BASE + "/ex/jira/" + siteId + "/rest/api/3";
            WebClient webClient = createWebClient(accessToken, apiUrl);
            
            log.info("Assign API URL: {}", apiUrl + "/issue/" + issueKey + "/assignee");
            
            Map<String, Object> body = new HashMap<>();
            body.put("accountId", assignee);
            
            log.debug("Assign request body: {}", body);
            
            String response = webClient
                .put()
                .uri("/issue/" + issueKey + "/assignee")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            log.debug("Assign response: {}", response);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully assigned {} to {} in {} ms", issueKey, assignee, duration);
            return true;
        } catch (Exception e) {
            log.error("Failed to assign {} to {}: {}", issueKey, assignee, e.getMessage(), e);
            // Try to get more details from the error response
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
                try {
                    String errorBody = ex.getResponseBodyAsString();
                    log.error("Error response body: {}", errorBody);
                } catch (Exception ignored) {}
            }
            return false;
        }
    }
    
    public String getCurrentUserAccountId(String accessToken) {
        try {
            WebClient webClient = WebClient.builder()
                .baseUrl(ATLASSIAN_API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
            
            Map<String, Object> response = (Map<String, Object>) webClient
                .get()
                .uri("/me")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            return (String) response.get("accountId");
        } catch (Exception e) {
            log.error("Failed to get current user accountId: {}", e.getMessage());
            return null;
        }
    }

    private WebClient createWebClient(String accessToken, String baseUrl) {
        log.debug("Creating WebClient with baseUrl: {}", baseUrl);
        
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
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