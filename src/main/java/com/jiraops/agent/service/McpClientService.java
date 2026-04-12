package com.jiraops.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
@Slf4j
public class McpClientService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Process mcpProcess;

    private synchronized Process getMcpProcess() throws IOException {
        if (mcpProcess == null || !mcpProcess.isAlive()) {
            log.info("Starting MCP server process...");
            ProcessBuilder pb = new ProcessBuilder("node", "mcp-server/src/index.js");
            pb.directory(new java.io.File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            mcpProcess = pb.start();
            
            // Wait a bit for server to start
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("MCP server started with PID: {}", mcpProcess.pid());
        }
        return mcpProcess;
    }

    public String callTool(String toolName, Map<String, Object> arguments) throws Exception {
        log.info("Calling MCP tool: {} with args: {}", toolName, arguments);
        
        Process process = getMcpProcess();
        
        // Build JSON-RPC request
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", System.currentTimeMillis());
        request.put("method", "tools/call");
        request.put("params", Map.of(
            "name", toolName,
            "arguments", arguments
        ));
        
        String requestJson = objectMapper.writeValueAsString(request);
        log.debug("MCP Request: {}", requestJson);
        
        // Write to stdin
        OutputStream out = process.getOutputStream();
        out.write((requestJson + "\n").getBytes());
        out.flush();
        
        // Read from stdout
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        
        if (line == null) {
            throw new RuntimeException("MCP server returned no response");
        }
        
        log.debug("MCP Response: {}", line);
        
        Map<String, Object> response = objectMapper.readValue(line, Map.class);
        
        if (response.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            throw new RuntimeException("MCP error: " + error.get("message"));
        }
        
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        List<Map<String, Object>> contents = (List<Map<String, Object>>) result.get("content");
        
        return (String) contents.get(0).get("text");
    }

    public List<Map<String, String>> listTools() throws Exception {
        Process process = getMcpProcess();
        
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", System.currentTimeMillis());
        request.put("method", "tools/list");
        request.put("params", Map.of());
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        OutputStream out = process.getOutputStream();
        out.write((requestJson + "\n").getBytes());
        out.flush();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        
        Map<String, Object> response = objectMapper.readValue(line, Map.class);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        
        List<Map<String, String>> toolList = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Map<String, String> t = new HashMap<>();
            t.put("name", (String) tool.get("name"));
            t.put("description", (String) tool.get("description"));
            toolList.add(t);
        }
        
        return toolList;
    }

    public void shutdown() {
        if (mcpProcess != null && mcpProcess.isAlive()) {
            mcpProcess.destroy();
            log.info("MCP server stopped");
        }
    }
}