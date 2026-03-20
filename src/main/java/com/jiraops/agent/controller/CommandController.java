package com.jiraops.agent.controller;

import com.jiraops.agent.model.dto.*;
import com.jiraops.agent.model.entity.ExecutionJob;
import com.jiraops.agent.service.CommandTemplateService;
import com.jiraops.agent.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommandController {

    private final CommandTemplateService commandTemplateService;
    private final ExecutionService executionService;

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
    public ResponseEntity<PreviewResult> preview(@PathVariable String commandId) {
        return ResponseEntity.ok(executionService.preview(commandId));
    }

    @PostMapping("/execute/{commandId}")
    public ResponseEntity<ExecutionResult> execute(@PathVariable String commandId) {
        return ResponseEntity.ok(executionService.execute(commandId));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<ExecutionJob>> getExecutionHistory() {
        return ResponseEntity.ok(executionService.getExecutionHistory());
    }
}
