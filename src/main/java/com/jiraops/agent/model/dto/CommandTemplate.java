package com.jiraops.agent.model.dto;

import com.jiraops.agent.model.enums.ActionType;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandTemplate {

    private String id;
    private String name;
    private String description;
    private ActionType actionType;
    private String jql;
    private Object parameters;
}
