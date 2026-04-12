package com.jiraops.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiraops.agent.model.dto.JiraIssueDto;
import com.jiraops.agent.service.tool.McpToolExecutor;
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
public class GroqMcpService {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "llama-3.3-70b-versatile";

    private final JiraToolService jiraToolService;
    private final ObjectMapper objectMapper;
    private final McpToolExecutor mcpToolExecutor;

    @Value("${groq.api-key}")
    private String apiKey;

    private WebClient webClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Initializing GroqMcpService with model: {}", MODEL_NAME);
        this.webClient = WebClient.builder()
                .baseUrl(GROQ_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.debug("GroqMcpService WebClient initialized");
    }

    public String processNaturalLanguageQuery(String query, String accessToken) {
        log.info("===========================================");
        log.info("Processing natural language query via MCP: {}", query);
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

        List<Map<String, Object>> tools = createToolDefinitions();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL_NAME);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt), 
            Map.of("role", "user", "content", query)
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

    private List<Map<String, Object>>  createToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // search_issues
        Map<String, Object> searchTool = new HashMap<>();
        searchTool.put("type", "function");
        Map<String, Object> searchFunc = new HashMap<>();
        searchFunc.put("name", "search_issues");
        searchFunc.put("description", "Search Jira issues using JQL query");
        Map<String, Object> searchParams = createObjectSchema(Map.of(
            "jql", createPropertySchema("string", "JQL query string"),
            "maxResults", createPropertySchema("integer", "Max results (default 50)")
        ), List.of("jql"));
        searchFunc.put("parameters", searchParams);
        searchTool.put("function", searchFunc);
        tools.add(searchTool);
        
        // transition_issue
        Map<String, Object> transitionTool = new HashMap<>();
        transitionTool.put("type", "function");
        Map<String, Object> transitionFunc = new HashMap<>();
        transitionFunc.put("name", "transition_issue");
        transitionFunc.put("description", "Change the status of a Jira issue");
        Map<String, Object> transitionParams = createObjectSchema(Map.of(
            "issueKey", createPropertySchema("string", "Issue key like PROJ-123"),
            "status", createPropertySchema("string", "Target status name")
        ), List.of("issueKey", "status"));
        transitionFunc.put("parameters", transitionParams);
        transitionTool.put("function", transitionFunc);
        tools.add(transitionTool);
        
        // add_comment
        Map<String, Object> commentTool = new HashMap<>();
        commentTool.put("type", "function");
        Map<String, Object> commentFunc = new HashMap<>();
        commentFunc.put("name", "add_comment");
        commentFunc.put("description", "Add a comment to a Jira issue");
        Map<String, Object> commentParams = createObjectSchema(Map.of(
            "issueKey", createPropertySchema("string", "Issue key"),
            "comment", createPropertySchema("string", "Comment text")
        ), List.of("issueKey", "comment"));
        commentFunc.put("parameters", commentParams);
        commentTool.put("function", commentFunc);
        tools.add(commentTool);
        
        // update_duedate
        Map<String, Object> dueDateTool = new HashMap<>();
        dueDateTool.put("type", "function");
        Map<String, Object> dueDateFunc = new HashMap<>();
        dueDateFunc.put("name", "update_duedate");
        dueDateFunc.put("description", "Update the due date of a Jira issue");
        Map<String, Object> dueDateParams = createObjectSchema(Map.of(
            "issueKey", createPropertySchema("string", "Issue key"),
            "dueDate", createPropertySchema("string", "Due date YYYY-MM-DD")
        ), List.of("issueKey", "dueDate"));
        dueDateFunc.put("parameters", dueDateParams);
        dueDateTool.put("function", dueDateFunc);
        tools.add(dueDateTool);
        
        // assign_issue
        Map<String, Object> assignTool = new HashMap<>();
        assignTool.put("type", "function");
        Map<String, Object> assignFunc = new HashMap<>();
        assignFunc.put("name", "assign_issue");
        assignFunc.put("description", "Assign a Jira issue to a user");
        Map<String, Object> assignParams = createObjectSchema(Map.of(
            "issueKey", createPropertySchema("string", "Issue key like PROJ-123"),
            "assignee", createPropertySchema("string", "Username or accountId (use 'me' for current user)")
        ), List.of("issueKey", "assignee"));
        assignFunc.put("parameters", assignParams);
        assignTool.put("function", assignFunc);
        tools.add(assignTool);
        
        // bulk_transition
        Map<String, Object> bulkTransTool = new HashMap<>();
        bulkTransTool.put("type", "function");
        Map<String, Object> bulkTransFunc = new HashMap<>();
        bulkTransFunc.put("name", "bulk_transition");
        bulkTransFunc.put("description", "Change status of multiple Jira issues matching a JQL filter");
        Map<String, Object> bulkTransParams = createObjectSchema(Map.of(
            "jql", createPropertySchema("string", "JQL query to select issues"),
            "status", createPropertySchema("string", "Target status name")
        ), List.of("jql", "status"));
        bulkTransFunc.put("parameters", bulkTransParams);
        bulkTransTool.put("function", bulkTransFunc);
        tools.add(bulkTransTool);
        
        // bulk_update_duedate
        Map<String, Object> bulkDueTool = new HashMap<>();
        bulkDueTool.put("type", "function");
        Map<String, Object> bulkDueFunc = new HashMap<>();
        bulkDueFunc.put("name", "bulk_update_duedate");
        bulkDueFunc.put("description", "Update due date of multiple Jira issues matching a JQL filter");
        Map<String, Object> bulkDueParams = createObjectSchema(Map.of(
            "jql", createPropertySchema("string", "JQL query to select issues"),
            "dueDate", createPropertySchema("string", "New due date in YYYY-MM-DD format")
        ), List.of("jql", "dueDate"));
        bulkDueFunc.put("parameters", bulkDueParams);
        bulkDueTool.put("function", bulkDueFunc);
        tools.add(bulkDueTool);
        
        // bulk_add_comment
        Map<String, Object> bulkCommTool = new HashMap<>();
        bulkCommTool.put("type", "function");
        Map<String, Object> bulkCommFunc = new HashMap<>();
        bulkCommFunc.put("name", "bulk_add_comment");
        bulkCommFunc.put("description", "Add a comment to multiple Jira issues matching a JQL filter");
        Map<String, Object> bulkCommParams = createObjectSchema(Map.of(
            "jql", createPropertySchema("string", "JQL query to select issues"),
            "comment", createPropertySchema("string", "Comment text to add")
        ), List.of("jql", "comment"));
        bulkCommFunc.put("parameters", bulkCommParams);
        bulkCommTool.put("function", bulkCommFunc);
        tools.add(bulkCommTool);
        
        // bulk_assign
        Map<String, Object> bulkAssignTool = new HashMap<>();
        bulkAssignTool.put("type", "function");
        Map<String, Object> bulkAssignFunc = new HashMap<>();
        bulkAssignFunc.put("name", "bulk_assign");
        bulkAssignFunc.put("description", "Assign multiple Jira issues matching a JQL filter");
        Map<String, Object> bulkAssignParams = createObjectSchema(Map.of(
            "jql", createPropertySchema("string", "JQL query to select issues"),
            "assignee", createPropertySchema("string", "Assignee - use 'me' for current user, or accountId")
        ), List.of("jql", "assignee"));
        bulkAssignFunc.put("parameters", bulkAssignParams);
        bulkAssignTool.put("function", bulkAssignFunc);
        tools.add(bulkAssignTool);
        
        return tools;
    }

    private Map<String, Object> createObjectSchema(Map<String, Map<String, Object>> properties, List<String> required) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> createPropertySchema(String type, String description) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
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

                            // Execute via MCP tool executor (logs all MCP protocol steps)
                            long startTime = System.currentTimeMillis();
                            String result = mcpToolExecutor.execute(toolName, params, accessToken);
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
}