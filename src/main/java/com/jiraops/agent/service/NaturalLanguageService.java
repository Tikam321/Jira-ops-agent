package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.NlQueryRequest;
import com.jiraops.agent.model.dto.NlQueryResponse;
import com.jiraops.agent.model.enums.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageService {

    private final GroqService groqService;

    public NlQueryResponse processQuery(NlQueryRequest request) {
        log.info("===========================================");
        log.info("NaturalLanguageService: Processing query");
        log.info("Original request: {}", request);
        log.info("===========================================");
        
        String naturalLanguageQuery = request.getQuery();
        
        try {
            log.info("Calling GroqService to process: {}", naturalLanguageQuery);
            long startTime = System.currentTimeMillis();
            
            String result = groqService.processNaturalLanguageQuery(naturalLanguageQuery);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Query processed successfully in {} ms", duration);
            
            ActionType actionType = determineActionType(naturalLanguageQuery);
            log.debug("Determined action type: {}", actionType);

            NlQueryResponse response = NlQueryResponse.builder()
                    .originalQuery(naturalLanguageQuery)
                    .generatedJql("")
                    .actionType(actionType)
                    .confidence(0.9)
                    .message(result)
                    .build();
            
            log.info("===========================================");
            log.info("Final Response:");
            log.info("Action Type: {}", response.getActionType());
            log.info("Confidence: {}", response.getConfidence());
            log.info("Message: {}", response.getMessage());
            log.info("===========================================");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing natural language query: {}", e.getMessage(), e);
            return NlQueryResponse.builder()
                    .originalQuery(naturalLanguageQuery)
                    .generatedJql("")
                    .actionType(ActionType.FETCH)
                    .confidence(0.0)
                    .message("Error processing query: " + e.getMessage())
                    .build();
        }
    }

    private ActionType determineActionType(String query) {
        String lowerQuery = query.toLowerCase();
        log.debug("Determining action type for query: {}", query);
        
        if (lowerQuery.contains("move") || lowerQuery.contains("change") || 
            lowerQuery.contains("transition") || lowerQuery.contains("set status")) {
            log.debug("Action determined: CHANGE_STATUS");
            return ActionType.CHANGE_STATUS;
        }
        
        if (lowerQuery.contains("due") || lowerQuery.contains("date") || 
            lowerQuery.contains("shift") || lowerQuery.contains("extend")) {
            log.debug("Action determined: UPDATE_DUEDATE");
            return ActionType.UPDATE_DUEDATE;
        }
        
        if (lowerQuery.contains("comment") || lowerQuery.contains("note")) {
            log.debug("Action determined: ADD_COMMENT");
            return ActionType.ADD_COMMENT;
        }
        
        if (lowerQuery.contains("assign") || lowerQuery.contains("assigned to")) {
            log.debug("Action determined: ASSIGN_ISSUE");
            return ActionType.ASSIGN_ISSUE;
        }
        
        log.debug("Action determined: FETCH (default)");
        return ActionType.FETCH;
    }
}
