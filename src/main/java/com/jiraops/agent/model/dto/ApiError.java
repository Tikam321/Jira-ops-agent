package com.jiraops.agent.model.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiError {
    private int status;
    private String message;
    private String path;
    private LocalDateTime timestamp;
}
