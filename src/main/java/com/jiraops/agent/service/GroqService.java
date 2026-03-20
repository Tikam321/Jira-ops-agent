package com.jiraops.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GroqService {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "llama-3.1-8b-instant";

    private final WebClient webClient;

    public GroqService(@Value("${groq.api-key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(GROQ_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generateJql(String naturalLanguageQuery) {
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
            Map<String, Object> response = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            return extractJqlFromResponse(response);
        } catch (Exception e) {
            log.error("Error calling Groq API: {}", e.getMessage());
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
                    return message.get("content").toString().trim();
                }
            }
            throw new RuntimeException("Invalid response from Groq API");
        } catch (Exception e) {
            log.error("Error extracting JQL from response: {}", e.getMessage());
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
