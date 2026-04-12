package com.jiraops.agent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.jira.client-id=test-client-id",
    "spring.security.oauth2.client.registration.jira.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
class CommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/commands"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnCommandsForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/commands"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnSpecificCommand() throws Exception {
        mockMvc.perform(get("/api/v1/commands/CMD001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("CMD001"))
            .andExpect(jsonPath("$.name").value("Fetch My Issues"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturn404ForInvalidCommand() throws Exception {
        mockMvc.perform(get("/api/v1/commands/INVALID"))
            .andExpect(status().isNotFound());
    }
}
