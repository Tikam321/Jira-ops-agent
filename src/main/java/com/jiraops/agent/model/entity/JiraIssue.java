package com.jiraops.agent.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jira_issues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraIssue {

    @Id
    private String id;

    private String key;
    private String summary;
    private String description;
    private String status;
    private String assignee;
    private String project;
    private LocalDateTime dueDate;
    private String issueType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
