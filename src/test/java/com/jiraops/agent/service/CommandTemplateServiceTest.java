package com.jiraops.agent.service;

import com.jiraops.agent.model.dto.CommandTemplate;
import com.jiraops.agent.model.enums.ActionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CommandTemplateServiceTest {

    @Autowired
    private CommandTemplateService commandTemplateService;

    @Test
    void shouldReturnAllCommands() {
        List<CommandTemplate> commands = commandTemplateService.getAllCommands();
        
        assertNotNull(commands);
        assertEquals(4, commands.size());
    }

    @Test
    void shouldReturnCommandById() {
        CommandTemplate command = commandTemplateService.getCommandById("CMD001");
        
        assertNotNull(command);
        assertEquals("Fetch My Issues", command.getName());
        assertEquals(ActionType.FETCH, command.getActionType());
    }

    @Test
    void shouldReturnNullForInvalidId() {
        CommandTemplate command = commandTemplateService.getCommandById("INVALID");
        
        assertNull(command);
    }

    @Test
    void shouldHaveCorrectJqlForEachCommand() {
        CommandTemplate dueDateCmd = commandTemplateService.getCommandById("CMD002");
        assertTrue(dueDateCmd.getJql().contains("duedate"));
        assertEquals(ActionType.UPDATE_DUEDATE, dueDateCmd.getActionType());

        CommandTemplate statusCmd = commandTemplateService.getCommandById("CMD003");
        assertTrue(statusCmd.getJql().contains("To Do"));
        assertEquals(ActionType.CHANGE_STATUS, statusCmd.getActionType());
    }
}
