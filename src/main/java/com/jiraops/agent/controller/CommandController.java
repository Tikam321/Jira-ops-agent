package com.jiraops.agent.controller;

import com.jiraops.agent.model.dto.*;
import com.jiraops.agent.model.entity.ExecutionJob;
import com.jiraops.agent.model.enums.ActionType;
import com.jiraops.agent.service.CommandTemplateService;
import com.jiraops.agent.service.ExecutionService;
import com.jiraops.agent.service.GroqMcpService;
import com.jiraops.agent.service.NaturalLanguageService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CommandController {

    private final CommandTemplateService commandTemplateService;
    private final ExecutionService executionService;
    private final NaturalLanguageService naturalLanguageService;
    private final GroqMcpService groqMcpService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    private String getAccessToken(OAuth2User oauth2User) {
        if (oauth2User == null) {
            log.warn("OAuth2User is null");
            return null;
        }
        var authorizedClient = authorizedClientService.loadAuthorizedClient("jira", oauth2User.getName());
        if (authorizedClient == null) {
            log.warn("No authorized client found for user: {}", oauth2User.getName());
            return null;
        }
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        log.debug("Access token obtained, expires at: {}", accessToken.getExpiresAt());
        return accessToken.getTokenValue();
    }

    @GetMapping("/commands")
    public ResponseEntity<List<CommandTemplate>> getAllCommands() {
        return ResponseEntity.ok(commandTemplateService.getAllCommands());
    }

    @GetMapping("/commands/{id}")
    public ResponseEntity<CommandTemplate> getCommand(@PathVariable String id) {
        CommandTemplate command = commandTemplateService.getCommandById(id);
        return command != null ? ResponseEntity.ok(command) : ResponseEntity.notFound().build();
    }

    @PostMapping("/preview/{commandId}")
    public ResponseEntity<PreviewResult> preview(@PathVariable String commandId, @AuthenticationPrincipal OAuth2User oauth2User) {
        String accessToken = getAccessToken(oauth2User);
        return ResponseEntity.ok(executionService.preview(commandId, accessToken));
    }

    @PostMapping("/execute/{commandId}")
    public ResponseEntity<ExecutionResult> execute(@PathVariable String commandId, @AuthenticationPrincipal OAuth2User oauth2User) {
        String accessToken = getAccessToken(oauth2User);
        return ResponseEntity.ok(executionService.execute(commandId, accessToken));
    }

    @PostMapping("/execute-by-action")
    public ResponseEntity<ExecutionResult> executeByAction(@RequestBody Map<String, String> request, @AuthenticationPrincipal OAuth2User oauth2User) {
        try {
            String accessToken = getAccessToken(oauth2User);
            if (accessToken == null) {
                return ResponseEntity.status(401).body(ExecutionResult.builder().message("Not authenticated").build());
            }
            
            String actionTypeStr = request.get("actionType");
            if (actionTypeStr == null || actionTypeStr.isEmpty()) {
                return ResponseEntity.badRequest().body(ExecutionResult.builder().message("actionType is required").build());
            }
            
            ActionType actionType = ActionType.valueOf(actionTypeStr);
            List<CommandTemplate> commands = commandTemplateService.getAllCommands();
            CommandTemplate matchingCommand = commands.stream().filter(c -> c.getActionType() == actionType).findFirst().orElse(null);
            
            if (matchingCommand == null) {
                return ResponseEntity.badRequest().body(ExecutionResult.builder().message("No command found for action type: " + actionType).build());
            }
            
            return ResponseEntity.ok(executionService.execute(matchingCommand.getId(), accessToken));
        } catch (Exception e) {
            log.error("Error executing action: ", e);
            return ResponseEntity.status(500).body(ExecutionResult.builder().message("Error: " + e.getMessage()).build());
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<ExecutionJob>> getExecutionHistory() {
        return ResponseEntity.ok(executionService.getExecutionHistory());
    }

    @PostMapping("/nl-query")
    public ResponseEntity<NlQueryResponse> naturalLanguageQuery(@Valid @RequestBody NlQueryRequest request, @AuthenticationPrincipal OAuth2User oauth2User) {
        String accessToken = getAccessToken(oauth2User);
        return ResponseEntity.ok(naturalLanguageService.processQuery(request, accessToken));
    }

    @PostMapping("/mcp-query")
    public ResponseEntity<NlQueryResponse> mcpQuery(@Valid @RequestBody NlQueryRequest request, @AuthenticationPrincipal OAuth2User oauth2User) {
        String accessToken = getAccessToken(oauth2User);
        
        log.info("MCP Query - User: {}, Token present: {}", 
            oauth2User != null ? oauth2User.getName() : "null",
            accessToken != null);
        
        if (accessToken == null || accessToken.isEmpty()) {
            return ResponseEntity.ok(NlQueryResponse.builder()
                .originalQuery(request.getQuery())
                .generatedJql("")
                .actionType(ActionType.FETCH)
                .confidence(0.0)
                .message("Not authenticated. Please login first at /login")
                .build());
        }
        
        try {
            String result = groqMcpService.processNaturalLanguageQuery(request.getQuery(), accessToken);
            
            return ResponseEntity.ok(NlQueryResponse.builder()
                .originalQuery(request.getQuery())
                .generatedJql("")
                .actionType(ActionType.FETCH)
                .confidence(0.95)
                .message(result)
                .build());
        } catch (Exception e) {
            log.error("Error in MCP query: {}", e.getMessage(), e);
            return ResponseEntity.ok(NlQueryResponse.builder()
                .originalQuery(request.getQuery())
                .generatedJql("")
                .actionType(ActionType.FETCH)
                .confidence(0.0)
                .message("Error: " + e.getMessage())
                .build());
        }
    }

    @GetMapping("/token")
    public ResponseEntity<?> getToken(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        
        var authorizedClient = authorizedClientService.loadAuthorizedClient("jira", oauth2User.getName());
        
        if (authorizedClient == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No OAuth token found"));
        }
        
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", accessToken.getTokenValue());
        response.put("token_type", accessToken.getTokenType().getValue());
        response.put("expires_at", accessToken.getExpiresAt() != null ? accessToken.getExpiresAt().toString() : null);
        
        if (authorizedClient.getRefreshToken() != null) {
            response.put("refresh_token", authorizedClient.getRefreshToken().getTokenValue());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal OAuth2User oauth2User) {
        log.info("=== /auth/me called ===");
        log.info("OAuth2User: {}", oauth2User);
        if (oauth2User == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("name", oauth2User.getAttribute("name"));
        response.put("email", oauth2User.getAttribute("email"));
        response.put("accountId", oauth2User.getAttribute("account_id"));
        response.put("picture", oauth2User.getAttribute("picture"));
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/auth/debug")
    public ResponseEntity<?> debugSession(@AuthenticationPrincipal OAuth2User oauth2User, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        response.put("oauth2UserPresent", oauth2User != null);
        response.put("principal", oauth2User != null ? oauth2User.getName() : null);
        response.put("sessionId", session.getId());
        response.put("sessionCreationTime", session.getCreationTime());
        return ResponseEntity.ok(response);
    }
}