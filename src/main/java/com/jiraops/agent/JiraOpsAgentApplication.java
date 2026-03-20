package com.jiraops.agent;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JiraOpsAgentApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().load();
        dotenv.entries().forEach(e ->
                System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(JiraOpsAgentApplication.class, args);
    }
}
