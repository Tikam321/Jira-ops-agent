package com.jiraops.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NlQueryRequest {

    @NotBlank(message = "Query cannot be empty")
    private String query;
    
    private String parameters;
}
