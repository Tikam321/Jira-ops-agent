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
    private final JiraToolService jiraToolService;

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

    public String processNaturalLanguageQuery(String query, String accessToken) {
        log.info("===========================================");
        log.info("Received natural language query: {}", query);
        log.info("===========================================");

        String systemPrompt = """
            You are a Jira assistant. Based on the user's request, determine the appropriate action and call the relevant tool.
            
            DECISION RULES:
            - Use SINGLE tool if user specifies a SPECIFIC issue (e.g., "move PROJ-123 to done")
            - Use BULK tool if user wants to update MULTIPLE issues (e.g., "move all my bugs to done", "shift due dates")
            
            Available SINGLE ISSUE tools:
            - search_issues: Search Jira issues using JQL. Use for queries like "show me my bugs", "find tasks"
            - transition_issue: Change the status of ONE specific issue. Use when user specifies exact issue key.
            - add_comment: Add a comment to ONE issue. Use when user specifies exact issue key.
            - update_duedate: Update the due date of ONE issue. Use when user specifies exact issue key.
            - assign_issue: Assign ONE issue to a user. Use when user specifies exact issue key.
            
            Available BULK tools (for multiple issues):
            - bulk_transition: Change status of MULTIPLE issues matching a JQL filter.
              Use for: "move all my bugs to done", "transition all in progress", "change status of my tasks"
              Params: jql (required), status (required)
            - bulk_update_duedate: Update due date of MULTIPLE issues matching a JQL filter.
              Use for: "shift due dates by 1 week", "extend deadlines", "set due date for all tasks"
              Params: jql (required), dueDate (required, YYYY-MM-DD)
              NOTE: To shift dates, first search to find issues, then use this with the same JQL
            - bulk_add_comment: Add comment to MULTIPLE issues matching a JQL filter.
              Use for: "add comment to all my bugs", "note on completed tasks"
              Params: jql (required), comment (required)
            - bulk_assign: Assign MULTIPLE issues matching a JQL filter.
              Use for: "assign all unassigned to me", "reassign my tasks"
              Params: jql (required), assignee (required, use "me" for current user)
            
            JQL GENERATION RULES:
            - For "all my bugs/stories/tasks": assignee = currentUser() AND issuetype = Bug/Story/Task
            - For "in progress": status = "In Progress"
            - For "done/completed": status = Done
            - For "due this week": duedate <= endOfWeek()
            - Combine with AND: assignee = currentUser() AND issuetype = Bug AND status = "To Do"
            
            EXAMPLES:
            Single: "move PROJ-123 to done" -> transition_issue with issueKey="PROJ-123", status="Done"
            Bulk: "move all my bugs to in progress" -> bulk_transition with jql="assignee = currentUser() AND issuetype = Bug AND status = 'To Do'", status="In Progress"
            Bulk: "assign all unassigned bugs to me" -> bulk_assign with jql="assignee is empty AND issuetype = Bug", assignee="me"
            
            Respond ONLY with a JSON object containing the tool call:
            {"tool": "tool_name", "params": {"param1": "value1", ...}}
            """;

        List<Map<String, Object>> tools = List.of(
                Map.of("type", "function", "function", Map.of("name", "search_issues", "description", "Search Jira issues using JQL query", "parameters", Map.of("type", "object", "properties", Map.of("jql", Map.of("type", "string", "description", "JQL query string"), "maxResults", Map.of("type", "integer", "description", "Max results (default 50)")), "required", List.of("jql")))),
                Map.of("type", "function", "function", Map.of("name", "transition_issue", "description", "Change the status of a Jira issue", "parameters", Map.of("type", "object", "properties", Map.of("issueKey", Map.of("type", "string", "description", "Issue key like PROJ-123"), "status", Map.of("type", "string", "description", "Target status name")), "required", List.of("issueKey", "status")))),
                Map.of("type", "function", "function", Map.of("name", "add_comment", "description", "Add a comment to a Jira issue", "parameters", Map.of("type", "object", "properties", Map.of("issueKey", Map.of("type", "string", "description", "Issue key"), "comment", Map.of("type", "string", "description", "Comment text")), "required", List.of("issueKey", "comment")))),
                Map.of("type", "function", "function", Map.of("name", "update_duedate", "description", "Update the due date of a Jira issue", "parameters", Map.of("type", "object", "properties", Map.of("issueKey", Map.of("type", "string", "description", "Issue key"), "dueDate", Map.of("type", "string", "description", "Due date YYYY-MM-DD")), "required", List.of("issueKey", "dueDate")))),
                Map.of("type", "function", "function", Map.of("name", "assign_issue", "description", "Assign a Jira issue to a user", "parameters", Map.of("type", "object", "properties", Map.of("issueKey", Map.of("type", "string", "description", "Issue key like PROJ-123"), "assignee", Map.of("type", "string", "description", "Username or email of the assignee")), "required", List.of("issueKey", "assignee")))),
                Map.of("type", "function", "function", Map.of("name", "bulk_transition", "description", "Change status of multiple Jira issues matching a JQL filter", "parameters", Map.of("type", "object", "properties", Map.of("jql", Map.of("type", "string", "description", "JQL query to select issues"), "status", Map.of("type", "string", "description", "Target status name")), "required", List.of("jql", "status")))),
                Map.of("type", "function", "function", Map.of("name", "bulk_update_duedate", "description", "Update due date of multiple Jira issues matching a JQL filter", "parameters", Map.of("type", "object", "properties", Map.of("jql", Map.of("type", "string", "description", "JQL query to select issues"), "dueDate", Map.of("type", "string", "description", "New due date in YYYY-MM-DD format")), "required", List.of("jql", "dueDate")))),
                Map.of("type", "function", "function", Map.of("name", "bulk_add_comment", "description", "Add a comment to multiple Jira issues matching a JQL filter", "parameters", Map.of("type", "object", "properties", Map.of("jql", Map.of("type", "string", "description", "JQL query to select issues"), "comment", Map.of("type", "string", "description", "Comment text to add")), "required", List.of("jql", "comment")))),
                Map.of("type", "function", "function", Map.of("name", "bulk_assign", "description", "Assign multiple Jira issues matching a JQL filter", "parameters", Map.of("type", "object", "properties", Map.of("jql", Map.of("type", "string", "description", "JQL query to select issues"), "assignee", Map.of("type", "string", "description", "Assignee - use 'me' for current user, or accountId")), "required", List.of("jql", "assignee"))))
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL_NAME);
        requestBody.put("messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", query)));
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

            String result = executeToolCall(response, accessToken);
            
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
    private String executeToolCall(Map<String, Object> response, String accessToken) {
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
                            
                            Map<String, Object> params = objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {});

                            log.debug("Parsed parameters: {}", params);
                            log.info("Executing tool: {} with params: {}", toolName, params);

                            long startTime = System.currentTimeMillis();
                            String result;
                            
                            switch (toolName) {
                                case "search_issues" -> {
                                    String jql = (String) params.get("jql");
                                    Integer maxResults = params.containsKey("maxResults") ? ((Number) params.get("maxResults")).intValue() : 50;
                                    log.info("Executing search_issues - JQL: {}, maxResults: {}", jql, maxResults);
                                    var issues = jiraApiService.searchIssues(jql, maxResults, accessToken);
                                    log.info("Search completed - Found {} issues", issues.size());
                                    result = formatIssuesResponse(issues);
                                }
                                case "transition_issue" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String status = (String) params.get("status");
                                    log.info("Executing transition_issue - issueKey: {}, status: {}", issueKey, status);
                                    boolean success = jiraApiService.transitionIssueByStatus(issueKey, status, accessToken);
                                    log.info("Transition result: {}", success);
                                    result = success ? "Successfully moved " + issueKey + " to " + status : "Failed to transition issue";
                                }
                                case "add_comment" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String comment = (String) params.get("comment");
                                    log.info("Executing add_comment - issueKey: {}, comment: {}", issueKey, comment);
                                    boolean success = jiraApiService.addComment(issueKey, comment, accessToken);
                                    log.info("Add comment result: {}", success);
                                    result = success ? "Added comment to " + issueKey : "Failed to add comment";
                                }
                                case "update_duedate" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String dueDate = (String) params.get("dueDate");
                                    log.info("Executing update_duedate - issueKey: {}, dueDate: {}", issueKey, dueDate);
                                    boolean success = jiraApiService.updateDuedate(issueKey, dueDate, accessToken);
                                    log.info("Update due date result: {}", success);
                                    result = success ? "Updated due date of " + issueKey + " to " + dueDate : "Failed to update due date";
                                }
                                case "assign_issue" -> {
                                    String issueKey = (String) params.get("issueKey");
                                    String assignee = (String) params.get("assignee");
                                    
                                    if (assignee == null || assignee.toLowerCase().contains("me") || assignee.toLowerCase().contains("myself") || assignee.isEmpty()) {
                                        String accountId = jiraApiService.getCurrentUserAccountId(accessToken);
                                        log.info("Resolved 'me' to accountId: {}", accountId);
                                        assignee = accountId != null ? accountId : assignee;
                                    }
                                    
                                    log.info("Executing assign_issue - issueKey: {}, assignee: {}", issueKey, assignee);
                                    boolean success = jiraApiService.assignIssue(issueKey, assignee, accessToken);
                                    log.info("Assign result: {}", success);
                                    result = success ? "Assigned " + issueKey + " to " + assignee : "Failed to assign issue";
                                }
                                case "bulk_transition" -> {
                                    String jql = (String) params.get("jql");
                                    String status = (String) params.get("status");
                                    log.info("Executing bulk_transition - JQL: {}, status: {}", jql, status);
                                    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
                                    log.info("Found {} issues matching JQL", issues.size());
                                    int successCount = 0;
                                    int failCount = 0;
                                    List<String> failedIssues = new ArrayList<>();
                                    for (JiraIssueDto issue : issues) {
                                        try {
                                            boolean success = jiraApiService.transitionIssueByStatus(issue.getKey(), status, accessToken);
                                            if (success) successCount++; else { failCount++; failedIssues.add(issue.getKey()); }
                                        } catch (Exception e) { failCount++; failedIssues.add(issue.getKey()); log.warn("Failed to transition {}: {}", issue.getKey(), e.getMessage()); }
                                    }
                                    result = failCount == 0 ? String.format("Successfully transitioned %d issues to '%s'", successCount, status) : String.format("Transitioned %d issues to '%s'. Failed: %d (%s)", successCount, status, failCount, String.join(", ", failedIssues));
                                    log.info("Bulk transition completed - {}", result);
                                }
                                case "bulk_update_duedate" -> {
                                    String jql = (String) params.get("jql");
                                    String dueDate = (String) params.get("dueDate");
                                    log.info("Executing bulk_update_duedate - JQL: {}, dueDate: {}", jql, dueDate);
                                    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
                                    log.info("Found {} issues matching JQL", issues.size());
                                    int successCount = 0;
                                    int failCount = 0;
                                    List<String> failedIssues = new ArrayList<>();
                                    for (JiraIssueDto issue : issues) {
                                        try {
                                            boolean success = jiraApiService.updateDuedate(issue.getKey(), dueDate, accessToken);
                                            if (success) successCount++; else { failCount++; failedIssues.add(issue.getKey()); }
                                        } catch (Exception e) { failCount++; failedIssues.add(issue.getKey()); log.warn("Failed to update due date for {}: {}", issue.getKey(), e.getMessage()); }
                                    }
                                    result = failCount == 0 ? String.format("Successfully updated due date to %s for %d issues", dueDate, successCount) : String.format("Updated due date for %d issues to %s. Failed: %d (%s)", successCount, dueDate, failCount, String.join(", ", failedIssues));
                                    log.info("Bulk update due date completed - {}", result);
                                }
                                case "bulk_add_comment" -> {
                                    String jql = (String) params.get("jql");
                                    String comment = (String) params.get("comment");
                                    log.info("Executing bulk_add_comment - JQL: {}, comment: {}", jql, comment);
                                    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
                                    log.info("Found {} issues matching JQL", issues.size());
                                    int successCount = 0;
                                    int failCount = 0;
                                    List<String> failedIssues = new ArrayList<>();
                                    for (JiraIssueDto issue : issues) {
                                        try {
                                            boolean success = jiraApiService.addComment(issue.getKey(), comment, accessToken);
                                            if (success) successCount++; else { failCount++; failedIssues.add(issue.getKey()); }
                                        } catch (Exception e) { failCount++; failedIssues.add(issue.getKey()); log.warn("Failed to add comment to {}: {}", issue.getKey(), e.getMessage()); }
                                    }
                                    result = failCount == 0 ? String.format("Successfully added comment to %d issues", successCount) : String.format("Added comment to %d issues. Failed: %d (%s)", successCount, failCount, String.join(", ", failedIssues));
                                    log.info("Bulk add comment completed - {}", result);
                                }
                                case "bulk_assign" -> {
                                    String jql = (String) params.get("jql");
                                    String assignee = (String) params.get("assignee");
                                    if (assignee != null && (assignee.toLowerCase().contains("me") || assignee.toLowerCase().contains("myself") || assignee.isEmpty())) {
                                        String accountId = jiraApiService.getCurrentUserAccountId(accessToken);
                                        log.info("Resolved 'me' to accountId: {}", accountId);
                                        assignee = accountId != null ? accountId : assignee;
                                    }
                                    log.info("Executing bulk_assign - JQL: {}, assignee: {}", jql, assignee);
                                    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100, accessToken);
                                    log.info("Found {} issues matching JQL", issues.size());
                                    int successCount = 0;
                                    int failCount = 0;
                                    List<String> failedIssues = new ArrayList<>();
                                    for (JiraIssueDto issue : issues) {
                                        try {
                                            boolean success = jiraApiService.assignIssue(issue.getKey(), assignee, accessToken);
                                            if (success) successCount++; else { failCount++; failedIssues.add(issue.getKey()); }
                                        } catch (Exception e) { failCount++; failedIssues.add(issue.getKey()); log.warn("Failed to assign {}: {}", issue.getKey(), e.getMessage()); }
                                    }
                                    result = failCount == 0 ? String.format("Successfully assigned %d issues to %s", successCount, assignee) : String.format("Assigned %d issues to %s. Failed: %d (%s)", successCount, assignee, failCount, String.join(", ", failedIssues));
                                    log.info("Bulk assign completed - {}", result);
                                }
                                default -> { log.warn("Unknown tool called: {}", toolName); result = "Unknown tool: " + toolName; }
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
        if (issues.isEmpty()) { return "No issues found matching your query."; }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(issues.size()).append(" issue(s):\n\n");
        for (Object issue : issues) {
            if (issue instanceof JiraIssueDto dto) {
                sb.append("- ").append(dto.getKey()).append(": ").append(dto.getSummary()).append("\n");
                sb.append("  Status: ").append(dto.getStatus()).append("\n");
                sb.append("  Type: ").append(dto.getIssueType()).append("\n");
                if (dto.getDueDate() != null) sb.append("  Due: ").append(dto.getDueDate()).append("\n");
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}