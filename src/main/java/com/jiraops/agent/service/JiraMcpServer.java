package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.JiraIssueDto;
import com.jiraops.agent.security.JiraOAuthUserPrincipal;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class JiraMcpServer {

    private final JiraApiService jiraApiService;
    private McpSyncServer server;
    private Thread serverThread;
    private volatile boolean running = true;

    public JiraMcpServer(JiraApiService jiraApiService) {
        this.jiraApiService = jiraApiService;
    }

    @PostConstruct
    public void startMcpServer() {
        log.info("Starting MCP Server...");

        try {
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(
                new JacksonMcpJsonMapper(JsonMapper.builder().build())
            );
            
            server = McpServer.sync(transportProvider)
                .serverInfo("jira-mcp-server", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                    .tools(true)
                    .build())
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("search_issues")
                        .description("Search Jira issues using JQL")
                        .inputSchema(createSearchIssuesSchema())
                        .build(),
                    (exchange, request) -> {
                        String jql = (String) request.arguments().get("jql");
                        Integer maxResults = request.arguments().get("maxResults") != null 
                            ? ((Number) request.arguments().get("maxResults")).intValue() : 50;

                        String accessToken = getAccessToken();
                        List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, maxResults, accessToken);

                        StringBuilder response = new StringBuilder();
                        response.append("Found ").append(issues.size()).append(" issue(s):\n\n");
                        for (JiraIssueDto issue : issues) {
                            response.append("- ").append(issue.getKey()).append(": ").append(issue.getSummary()).append("\n");
                            response.append("  Status: ").append(issue.getStatus()).append("\n");
                            response.append("  Type: ").append(issue.getIssueType()).append("\n");
                            if (issue.getDueDate() != null) {
                                response.append("  Due: ").append(issue.getDueDate()).append("\n");
                            }
                            response.append("\n");
                        }
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(response.toString())))
                            .build();
                    }
                )
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("get_issue")
                        .description("Get a specific Jira issue by key")
                        .inputSchema(createGetIssueSchema())
                        .build(),
                    (exchange, request) -> {
                        String issueKey = (String) request.arguments().get("issueKey");
                        String accessToken = getAccessToken();
                        
                        List<JiraIssueDto> issues = jiraApiService.searchIssues("key = " + issueKey, 1, accessToken);
                        if (issues.isEmpty()) {
                            return CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent("Issue not found: " + issueKey)))
                                .build();
                        }
                        
                        JiraIssueDto issue = issues.get(0);
                        String text = issue.getKey() + ": " + issue.getSummary() + "\n" +
                            "Status: " + issue.getStatus() + "\n" +
                            "Type: " + issue.getIssueType() + "\n" +
                            "Assignee: " + issue.getAssignee() + "\n" +
                            "Project: " + issue.getProject();
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(text)))
                            .build();
                    }
                )
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("update_issue")
                        .description("Update fields on a Jira issue")
                        .inputSchema(createUpdateIssueSchema())
                        .build(),
                    (exchange, request) -> {
                        String issueKey = (String) request.arguments().get("issueKey");
                        String accessToken = getAccessToken();
                        
                        Map<String, Object> fields = new HashMap<>();
                        if (request.arguments().get("summary") != null) {
                            fields.put("summary", request.arguments().get("summary"));
                        }
                        if (request.arguments().get("description") != null) {
                            fields.put("description", request.arguments().get("description"));
                        }
                        
                        if (fields.isEmpty()) {
                            return CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent("No fields to update")))
                                .build();
                        }
                        
                        boolean result = jiraApiService.updateIssue(issueKey, fields, accessToken);
                        String text = result ? "Successfully updated " + issueKey : "Failed to update " + issueKey;
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(text)))
                            .build();
                    }
                )
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("transition_issue")
                        .description("Transition a Jira issue to a new status")
                        .inputSchema(createTransitionIssueSchema())
                        .build(),
                    (exchange, request) -> {
                        String issueKey = (String) request.arguments().get("issueKey");
                        String status = (String) request.arguments().get("status");
                        String accessToken = getAccessToken();
                        
                        boolean result = jiraApiService.transitionIssueByStatus(issueKey, status, accessToken);
                        String text;
                        if (result) {
                            text = "Successfully transitioned " + issueKey + " to " + status;
                        } else {
                            List<Map<String, String>> transitions = jiraApiService.getTransitions(issueKey, accessToken);
                            String available = String.join(", ", transitions.stream()
                                .map(t -> t.get("name"))
                                .toList());
                            text = "Status '" + status + "' not found. Available: " + available;
                        }
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(text)))
                            .build();
                    }
                )
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("add_comment")
                        .description("Add a comment to a Jira issue")
                        .inputSchema(createAddCommentSchema())
                        .build(),
                    (exchange, request) -> {
                        String issueKey = (String) request.arguments().get("issueKey");
                        String comment = (String) request.arguments().get("comment");
                        String accessToken = getAccessToken();
                        
                        boolean result = jiraApiService.addComment(issueKey, comment, accessToken);
                        String text = result ? "Successfully added comment to " + issueKey : "Failed to add comment";
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(text)))
                            .build();
                    }
                )
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("update_duedate")
                        .description("Update the due date of a Jira issue")
                        .inputSchema(createUpdateDuedateSchema())
                        .build(),
                    (exchange, request) -> {
                        String issueKey = (String) request.arguments().get("issueKey");
                        String dueDate = (String) request.arguments().get("dueDate");
                        String accessToken = getAccessToken();
                        
                        boolean result = jiraApiService.updateDuedate(issueKey, dueDate, accessToken);
                        String text = result ? "Updated due date for " + issueKey + " to " + dueDate : "Failed to update due date";
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(text)))
                            .build();
                    }
                )
                .toolCall(
                    McpSchema.Tool.builder()
                        .name("assign_issue")
                        .description("Assign a Jira issue to a user")
                        .inputSchema(createAssignIssueSchema())
                        .build(),
                    (exchange, request) -> {
                        String issueKey = (String) request.arguments().get("issueKey");
                        String assignee = (String) request.arguments().get("assignee");
                        String accessToken = getAccessToken();
                        
                        boolean result = jiraApiService.assignIssue(issueKey, assignee, accessToken);
                        String text = result ? "Assigned " + issueKey + " to " + assignee : "Failed to assign issue";
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(text)))
                            .build();
                    }
                )
                .build();

            serverThread = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            log.info("MCP Server started successfully");

        } catch (Exception e) {
            log.error("Failed to start MCP Server: {}", e.getMessage(), e);
        }
    }

    private JsonSchema createSearchIssuesSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jql", createPropertySchema("string", "JQL query string"));
        properties.put("maxResults", createPropertySchema("number", "Maximum number of results"));
        return new JsonSchema("object", properties, List.of("jql"), null, null, null);
    }

    private JsonSchema createGetIssueSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("issueKey", createPropertySchema("string", "Jira issue key (e.g., PROJ-123)"));
        return new JsonSchema("object", properties, List.of("issueKey"), null, null, null);
    }

    private JsonSchema createUpdateIssueSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("issueKey", createPropertySchema("string", "Jira issue key"));
        properties.put("summary", createPropertySchema("string", "New summary"));
        properties.put("description", createPropertySchema("string", "New description"));
        return new JsonSchema("object", properties, List.of("issueKey"), null, null, null);
    }

    private JsonSchema createTransitionIssueSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("issueKey", createPropertySchema("string", "Jira issue key"));
        properties.put("status", createPropertySchema("string", "Target status name"));
        return new JsonSchema("object", properties, List.of("issueKey", "status"), null, null, null);
    }

    private JsonSchema createAddCommentSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("issueKey", createPropertySchema("string", "Jira issue key"));
        properties.put("comment", createPropertySchema("string", "Comment text"));
        return new JsonSchema("object", properties, List.of("issueKey", "comment"), null, null, null);
    }

    private JsonSchema createUpdateDuedateSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("issueKey", createPropertySchema("string", "Jira issue key"));
        properties.put("dueDate", createPropertySchema("string", "Due date (YYYY-MM-DD)"));
        return new JsonSchema("object", properties, List.of("issueKey", "dueDate"), null, null, null);
    }

    private JsonSchema createAssignIssueSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("issueKey", createPropertySchema("string", "Jira issue key"));
        properties.put("assignee", createPropertySchema("string", "Username to assign to"));
        return new JsonSchema("object", properties, List.of("issueKey", "assignee"), null, null, null);
    }

    private Map<String, Object> createPropertySchema(String type, String description) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }

    private String getAccessToken() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JiraOAuthUserPrincipal jiraUser) {
                Map<String, Object> attrs = jiraUser.getAttributes();
                return (String) attrs.get("access_token");
            }
        } catch (Exception e) {
            log.warn("Could not get access token: {}", e.getMessage());
        }
        return null;
    }

    @PreDestroy
    public void stopMcpServer() {
        log.info("Stopping MCP Server...");
        running = false;
        if (server != null) {
            server.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
}