package com.jiraops.agent.model.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewChange {
    private String issueKey;
    private String field;
    private String currentValue;
    private String newValue;
}
