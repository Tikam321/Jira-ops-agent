package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.JiraIssueDto;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class JiraApiService {

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.email}")
    private String jiraEmail;

    @Value("${jira.api-token}")
    private String jiraApiToken;

    private  WebClient webClient;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
                .baseUrl(jiraBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(
                                (jiraEmail + ":" + jiraApiToken).getBytes()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public List<JiraIssueDto> searchIssues(String jql, int maxResults) {
        try {
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

            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
            return parseIssues(issues);
        } catch (Exception e) {
            throw new RuntimeException("Failed to search Jira issues: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getTransitions(String issueKey) {
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
                for (Map<String, Object> t : transitionList) {
                    Map<String, String> item = new HashMap<>();
                    item.put("id", String.valueOf(t.get("id")));
                    item.put("name", (String) t.get("name"));
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get transitions: " + e.getMessage());
        }
    }

    public boolean updateIssue(String issueKey, Map<String, Object> fields) {
        try {
            webClient
                .put()
                .uri("/rest/api/3/issue/" + issueKey)
                .bodyValue(Map.of("fields", fields))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update issue: " + e.getMessage());
        }
    }

    public boolean transitionIssue(String issueKey, String transitionId) {
        try {
            webClient
                .post()
                .uri("/rest/api/3/issue/" + issueKey + "/transitions")
                .bodyValue(Map.of("transition", Map.of("id", transitionId)))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transition issue: " + e.getMessage());
        }
    }

    private List<JiraIssueDto> parseIssues(List<Map<String, Object>> issues) {
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
            
            result.add(dto);
        }
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
