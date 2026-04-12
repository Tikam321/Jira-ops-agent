package com.jiraops.agent.service.tool;

import com.jiraops.agent.model.dto.JiraIssueDto;
import com.jiraops.agent.service.JiraToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpToolExecutor {

    private final JiraToolService jiraToolService;

    public String execute(String toolName, Map<String, Object> params, String accessToken) {
        log.info("===========================================");
        log.info("MCP TOOL EXECUTION");
        log.info("Tool: {}", toolName);
        log.info("Params: {}", params);
        log.info("===========================================");

        // Simulate MCP protocol logging
        logMcpProtocolMessage("tools/call", Map.of(
            "name", toolName,
            "arguments", params
        ));

        String result;
        try {
            result = switch (toolName) {
                case "search_issues" -> {
                    String jql = (String) params.get("jql");
                    Integer maxResults = params.containsKey("maxResults") ? 
                        ((Number) params.get("maxResults")).intValue() : 50;
                    List<JiraIssueDto> issues = jiraToolService.searchIssues(jql, maxResults, accessToken);
                    yield jiraToolService.formatIssues(issues);
                }
                case "transition_issue" -> {
                    String issueKey = (String) params.get("issueKey");
                    String status = (String) params.get("status");
                    yield jiraToolService.transitionIssue(issueKey, status, accessToken);
                }
                case "add_comment" -> {
                    String issueKey = (String) params.get("issueKey");
                    String comment = (String) params.get("comment");
                    yield jiraToolService.addComment(issueKey, comment, accessToken);
                }
                case "update_duedate" -> {
                    String issueKey = (String) params.get("issueKey");
                    String dueDate = (String) params.get("dueDate");
                    yield jiraToolService.updateDuedate(issueKey, dueDate, accessToken);
                }
                case "assign_issue" -> {
                    String issueKey = (String) params.get("issueKey");
                    String assignee = (String) params.get("assignee");
                    yield jiraToolService.assignIssue(issueKey, assignee, accessToken);
                }
                case "bulk_transition" -> {
                    String jql = (String) params.get("jql");
                    String status = (String) params.get("status");
                    yield jiraToolService.bulkTransition(jql, status, accessToken);
                }
                case "bulk_update_duedate" -> {
                    String jql = (String) params.get("jql");
                    String dueDate = (String) params.get("dueDate");
                    yield jiraToolService.bulkUpdateDuedate(jql, dueDate, accessToken);
                }
                case "bulk_add_comment" -> {
                    String jql = (String) params.get("jql");
                    String comment = (String) params.get("comment");
                    yield jiraToolService.bulkAddComment(jql, comment, accessToken);
                }
                case "bulk_assign" -> {
                    String jql = (String) params.get("jql");
                    String assignee = (String) params.get("assignee");
                    yield jiraToolService.bulkAssign(jql, assignee, accessToken);
                }
                default -> {
                    log.warn("Unknown tool: {}", toolName);
                    yield "Unknown tool: " + toolName;
                }
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage(), e);
            result = "Error: " + e.getMessage();
        }

        // Simulate MCP protocol response logging
        logMcpProtocolResponse(Map.of(
            "content", List.of(Map.of("type", "text", "text", result))
        ));

        log.info("===========================================");
        log.info("MCP TOOL RESULT: {}", result);
        log.info("===========================================");

        return result;
    }

    private void logMcpProtocolMessage(String method, Map<String, Object> params) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ MCP OUTGOING: {}", String.format("%-45s", method + "       "));
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ {}", String.format("%-60s", ""));
        log.info("│ Params: {}", String.format("%-54s", params.toString()));
        log.info("└─────────────────────────────────────────────────────────────┘");
        log.info("");
    }

    private void logMcpProtocolResponse(Map<String, Object> response) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ MCP INCOMING: tools/call result                               │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ {}", String.format("%-60s", ""));
        log.info("│ Response: {}", String.format("%-54s", response.toString()));
        log.info("└─────────────────────────────────────────────────────────────┘");
        log.info("");
    }
}