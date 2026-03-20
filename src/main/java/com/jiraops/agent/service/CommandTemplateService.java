package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.CommandTemplate;
import com.jiraops.agent.model.enums.ActionType;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class CommandTemplateService {

    private static final List<CommandTemplate> COMMANDS = Arrays.asList(
        CommandTemplate.builder()
            .id("CMD001")
            .name("Fetch My Issues")
            .description("Fetch all issues assigned to current user")
            .actionType(ActionType.FETCH)
            .jql("assignee = currentUser() ORDER BY updated DESC")
            .parameters(null)
            .build(),

        CommandTemplate.builder()
            .id("CMD002")
            .name("Shift Due Date +1 Month")
            .description("Shift due dates forward by 1 month for unresolved issues")
            .actionType(ActionType.UPDATE_DUEDATE)
            .jql("assignee = currentUser() AND duedate <= endOfMonth() AND status NOT IN (Done, Closed)")
            .parameters("{\"type\": \"SHIFT_DUEDATE\", \"delta\": \"+1M\"}")
            .build(),

        CommandTemplate.builder()
            .id("CMD003")
            .name("Change Status: To Do → In Progress")
            .description("Move all To Do issues to In Progress")
            .actionType(ActionType.CHANGE_STATUS)
            .jql("assignee = currentUser() AND status = \"To Do\"")
            .parameters("{\"fromStatus\": \"To Do\", \"toStatus\": \"In Progress\"}")
            .build(),

        CommandTemplate.builder()
            .id("CMD004")
            .name("Change Status: In Progress → Done")
            .description("Move all In Progress issues to Done")
            .actionType(ActionType.CHANGE_STATUS)
            .jql("assignee = currentUser() AND status = \"In Progress\"")
            .parameters("{\"fromStatus\": \"In Progress\", \"toStatus\": \"Done\"}")
            .build()
    );

    public List<CommandTemplate> getAllCommands() {
        return COMMANDS;
    }

    public CommandTemplate getCommandById(String id) {
        return COMMANDS.stream()
            .filter(cmd -> cmd.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
}
