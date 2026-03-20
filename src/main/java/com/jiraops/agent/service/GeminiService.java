package com.jiraops.agent.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GeminiService {

    private final Client client;
    private static final String MODEL_NAME = "gemini-2.0-flash";

    public GeminiService(@Value("${gemini.api-key}") String apiKey) {
        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
    }

    public String generateJql(String naturalLanguageQuery) {
        String prompt = buildJqlPrompt(naturalLanguageQuery);
        
        try {
            Content userContent = Content.builder()
                    .role("user")
                    .parts(List.of(com.google.genai.types.Part.builder()
                            .text(prompt)
                            .build()))
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    MODEL_NAME,
                    userContent,
                    GenerateContentConfig.builder()
                            .temperature(0.3f)
                            .maxOutputTokens(256)
                            .build()
            );

            return response.text().trim();
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            throw new RuntimeException("Failed to generate JQL from Gemini: " + e.getMessage());
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
