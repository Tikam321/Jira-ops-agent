package com.jiraops.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableAsync
@Slf4j
public class JiraOpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraOpsAgentApplication.class, args);
    }

    @PostConstruct
    public void init() {
        log.info("===========================================");
        log.info("Jira Ops Agent Application Started");
        log.info("Session Store Type: JDBC (PostgreSQL)");
        log.info("===========================================");
    }
}
