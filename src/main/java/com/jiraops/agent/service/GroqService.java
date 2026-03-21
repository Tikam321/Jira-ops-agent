package com.jiraops.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiraops.agent.model.dto.JiraIssueDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "llama-3.3-70b-versatile";

    private final JiraApiService jiraApiService;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String apiKey;

    private WebClient webClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Initializing GroqService with model: {}", MODEL_NAME);
        this.webClient = WebClient.builder()
                .baseUrl(GROQ_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.debug("GroqService WebClient initialized");
    }

    public String processNaturalLanguageQuery(String query) {
        log.info("===========================================");
        log.info("Received natural language query: {}", query);
        log.info("===========================================");

        String systemPrompt = """
            You are a Jira assistant. Based on the user's request, determine the appropriate action and call the relevant tool.
            
            Available tools:
            - search_issues: Search Jira issues using JQL. Use for queries like "show me my bugs", "find tasks"
            - transition_issue: Change the status of an issue. Use for "move to done", "change status"
            - add_comment: Add a comment to an issue. Use for "add comment", "add note"
            - update_duedate: Update the due date of an issue. Use for "set due date", "update deadline"
            - assign_issue: Assign a Jira issue to a user. Use for "assign to", "assign this to me". Pass the assignee as "me" if user wants to assign to themselves.
            
            Respond ONLY with a JSON object containing the tool call:
            {"tool": "tool_name", "params": {"param1": "value1", ...}}
            
            If the user wants to search, always include assignee = currentUser() in JQL unless specified otherwise.
            If the user wants to assign to themselves, pass "me" as the assignee parameter.
            """;

        List<Map<String, Object>> tools = List.of(
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "search_issues",
                                "description", "Search Jira issues using JQL query",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "jql", Map.of("type", "string", "description", "JQL query string"),
                                                "maxResults", Map.of("type", "integer", "description", "Max results (default 50)")
                                        ),
                                        "required", List.of("jql")
                                )
                        )
                ),
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "transition_issue",
                                "description", "Change the status of a Jira issue",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "issueKey", Map.of("type", "string", "description", "Issue key like PROJ-123"),
                                                "status", Map.of("type", "string", "description", "Target status name")
                                        ),
                                        "required", List.of("issueKey", "status")
                                )
                        )
                ),
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "add_comment",
                                "description", "Add a comment to a Jira issue",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "issueKey", Map.of("type", "string", "description", "Issue key"),
                                                "comment", Map.of("type", "string", "description", "Comment text")
                                        ),
                                        "required", List.of("issueKey", "comment")
                                )
                        )
                ),
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "update_duedate",
                                "description", "Update the due date of a Jira issue",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "issueKey", Map.of("type", "string", "description", "Issue key"),
                                                "dueDate", Map.of("type", "string", "description", "Due date YYYY-MM-DD")
                                        ),
                                        "required", List.of("issueKey", "dueDate")
                                )
                        )
                ),
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "assign_issue",
                                "description", "Assign a Jira issue to a user",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "issueKey", Map.of("type", "string", "description", "Issue key like PROJ-123"),
                                                "assignee", Map.of("type", "string", "description", "Username or email of the assignee")
                                        ),
                                        "required", List.of("issueKey", "assignee")
                                )
                        )
                )
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL_NAME);
        requestBody.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", systemPrompt
                ),
                Map.of(
                        "role", "user",
                        "content", query
                )
        ));
        requestBody.put("tools", tools);
        requestBody.put("tool_choice", "auto");
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 512);

        log.debug("Groq API Request: {}", requestBody);

        try {
            log.info("Calling Groq API...");
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> response = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));

            long duration = System.currentTimeMillis() - startTime;
            log.info("Groq API response received in {} ms", duration);
            log.debug("Groq API Response: {}", response);

            String result = executeToolCall(response);
            
            log.info("===========================================");
            log.info("Final result: {}", result);
            log.info("===========================================");
            
            return result;
        } catch (Exception e) {
            log.error("Error calling Groq API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process query: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String executeToolCall(Map<String, Object> response) {
        try {
            log.debug("Parsing Groq response for tool calls...");
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    log.debug("Message from LLM: {}", message);

                    if (message.containsKey("tool_calls")) {
                        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                        log.info("LLM returned {} tool call(s)", toolCalls.size());
                        
                        if (!toolCalls.isEmpty()) {
                            Map<String, Object> toolCall = toolCalls.get(0);
                            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                            String toolName = (String) function.get("name");
                            String arguments = (String) function.get("arguments");
                            
                            log.info("-------------------------------------------");
                            log.info("Tool Selected: {}", toolName);
                            log.info("Raw Arguments: {}", arguments);
                            log.info("-------------------------------------------");
                            
                            Map<String, Object> params = objectMapper.readValue(
                                    arguments,
                                    new TypeReference<Map<String, Object>>() {}
                            );

                            log.debug("Parsed parameters: {}", params);
                            log.info("Executing tool: {} with params: {}", toolName, params);

                            long startTime = System.currentTimeMillis();
                            String result;
                            
                            switch (toolName) {
                                case "search_issues" -> {
                                    String jql = (String) params.get("jql");
                                    Integer maxResults = params.containsKey("maxResults")
                                            ? ((Number) params.get("maxResults")).intValue()
                                            : 50;
                                    log.info("Executing search_issues - JQL: {}, maxResults: {}", jql, maxResults);
                                    var issues = jiraApiService.searchIssues(jql, maxResults);
                                    log.info("Search completed - Found {} issues", issues.size());
                                    result = formatIssuesResponse(issues);
                                }
                                case "transition_issue" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String status = (String) params.get("status");
                                    log.info("Executing transition_issue - issueKey: {}, status: {}", issueKey, status);
                                    boolean success = jiraApiService.transitionIssueByStatus(issueKey, status);
                                    log.info("Transition result: {}", success);
                                    result = success ? "Successfully moved " + issueKey + " to " + status : "Failed to transition issue";
                                }
                                case "add_comment" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String comment = (String) params.get("comment");
                                    log.info("Executing add_comment - issueKey: {}, comment: {}", issueKey, comment);
                                    boolean success = jiraApiService.addComment(issueKey, comment);
                                    log.info("Add comment result: {}", success);
                                    result = success ? "Added comment to " + issueKey : "Failed to add comment";
                                }
                                case "update_duedate" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String dueDate = (String) params.get("dueDate");
                                    log.info("Executing update_duedate - issueKey: {}, dueDate: {}", issueKey, dueDate);
                                    boolean success = jiraApiService.updateDuedate(issueKey, dueDate);
                                    log.info("Update due date result: {}", success);
                                    result = success ? "Updated due date of " + issueKey + " to " + dueDate : "Failed to update due date";
                                }
                                case "assign_issue" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String assignee = (String) params.get("assignee");
                                    
                                    if (assignee == null || assignee.toLowerCase().contains("me") || 
                                        assignee.toLowerCase().contains("myself") || assignee.isEmpty()) {
                                        String accountId = jiraApiService.getCurrentUserAccountId();
                                        log.info("Resolved 'me' to accountId: {}", accountId);
                                        assignee = accountId != null ? accountId : assignee;
                                    }
                                    
                                    log.info("Executing assign_issue - issueKey: {}, assignee: {}", issueKey, assignee);
                                    boolean success = jiraApiService.assignIssue(issueKey, assignee);
                                    log.info("Assign result: {}", success);
                                    result = success ? "Assigned " + issueKey + " to " + assignee : "Failed to assign issue";
                                }
                                default -> {
                                    log.warn("Unknown tool called: {}", toolName);
                                    result = "Unknown tool: " + toolName;
                                }
                            };
                            
                            long duration = System.currentTimeMillis() - startTime;
                            log.info("Tool execution completed in {} ms", duration);
                            log.debug("Tool result: {}", result);
                            
                            return result;
                        }
                    }

                    log.warn("No tool_calls found in LLM response");
                    String content = (String) message.get("content");
                    log.debug("LLM direct response content: {}", content);
                    return content != null ? content : "No tool call returned";
                }
            }
            log.error("Invalid response structure from Groq API");
            throw new RuntimeException("Invalid response from Groq API");
        } catch (Exception e) {
            log.error("Error executing tool call: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to execute tool: " + e.getMessage());
        }
    }

    private String formatIssuesResponse(List<?> issues) {
        if (issues.isEmpty()) {
            log.debug("No issues found, returning empty response");
            return "No issues found matching your query.";
        }

        log.debug("Formatting response for {} issues", issues.size());
        
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(issues.size()).append(" issue(s):\n\n");

        for (Object issue : issues) {
            if (issue instanceof JiraIssueDto dto) {
                sb.append("- ").append(dto.getKey()).append(": ").append(dto.getSummary()).append("\n");
                sb.append("  Status: ").append(dto.getStatus()).append("\n");
                sb.append("  Type: ").append(dto.getIssueType()).append("\n");
                if (dto.getDueDate() != null) {
                    sb.append("  Due: ").append(dto.getDueDate()).append("\n");
                }
                sb.append("\n");
            }
        }
        
        log.debug("Formatted issues response:\n{}", sb);
        return sb.toString();
    }

    public String generateJql(String naturalLanguageQuery) {
        log.info("Generating JQL for query: {}", naturalLanguageQuery);
        
        String prompt = buildJqlPrompt(naturalLanguageQuery);

        Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", 0.3,
                "max_tokens", 256
        );

        try {
            log.debug("Calling Groq API for JQL generation...");
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> response = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            long duration = System.currentTimeMillis() - startTime;
            log.info("JQL generation completed in {} ms", duration);
            log.debug("Groq JQL Response: {}", response);

            String jql = extractJqlFromResponse(response);
            log.info("Generated JQL: {}", jql);
            
            return jql;
        } catch (Exception e) {
            log.error("Error generating JQL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate JQL from Groq: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractJqlFromResponse(Map<String, Object> response) {
        try {
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    String content = message.get("content").toString().trim();
                    log.debug("Extracted JQL content: {}", content);
                    return content;
                }
            }
            log.error("Invalid response structure for JQL extraction");
            throw new RuntimeException("Invalid response from Groq API");
        } catch (Exception e) {
            log.error("Error extracting JQL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse Groq response");
        }
    }

    private String buildJqlPrompt(String query) {
        return """
            You are a Jira JQL (Jira Query Language) expert. Convert the following natural language query into a valid JQL query.
            
            IMPORTANT RULES:
            1. Always include "assignee = currentUser()" to filter by the current user
            2. Use proper JQL syntax (field = value)
            3. For issue types, use: issuetype = Story, issuetype = Bug, issuetype = Task, etc.
            4. For status, use: status = "To Do", status = "In Progress", status = Done, etc.
            5. For dates, use: duedate <= "2026-03-27", duedate >= startOfWeek(), duedate <= endOfWeek(), etc.
            6. For projects, use: project = PROJECTNAME
            7. For summaries containing spaces, use: summary ~ "search term"
            8. Combine conditions with AND/OR
            
            EXAMPLES:
            - "my bugs due this week" -> assignee = currentUser() AND issuetype = Bug AND duedate <= endOfWeek()
            - "all stories in progress" -> assignee = currentUser() AND issuetype = Story AND status = "In Progress"
            - "completed tasks" -> assignee = currentUser() AND issuetype = Task AND status = Done
            - "issues in SCRUM project" -> project = SCRUM AND assignee = currentUser()
            
            Convert this query:
            """ + query + """
            
            Return ONLY the JQL query, nothing else. No quotes, no explanation, just the JQL.
            """;
    }
}
