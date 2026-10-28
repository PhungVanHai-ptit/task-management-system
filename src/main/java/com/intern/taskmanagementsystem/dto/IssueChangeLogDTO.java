package com.intern.taskmanagementsystem.dto;

import com.intern.taskmanagementsystem.enums.IssueChangeField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueChangeLogDTO {
    private Long id;
    private Long issueId;
    private IssueChangeField fieldName;
    private String oldValue;
    private String newValue;
    private Long transitionTimeMinutes;
    private LocalDateTime changedAt;
    private UserDTO.SummaryResponse changedBy;

    // Enriched user details for ASSIGNEE change logs (avoiding N+1 queries)
    private UserDTO.SummaryResponse oldAssignee;
    private UserDTO.SummaryResponse newAssignee;
}
