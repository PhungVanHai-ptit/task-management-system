package com.intern.taskmanagementsystem.dto;

import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class IssueDTO {

    private IssueDTO() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200)
        private String title;

        private String description;

        private IssuePriority priority = IssuePriority.MEDIUM;

        @FutureOrPresent(message = "Deadline phải lớn hơn hoặc bằng thời gian hiện tại")
        private LocalDateTime dueDate;

        private Long assigneeId;

        private Set<Long> labelIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 200)
        private String title;

        private String description;

        private IssuePriority priority;
        private LocalDateTime dueDate;
        private Long assigneeId;

        private Boolean unassign;

        private IssueStatus status;

        private Long version;

        private Set<Long> labelIds;

        private Boolean isPublic;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateVisibilityRequest {
        @NotNull(message = "Trạng thái công khai không được để trống")
        private Boolean isPublic;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeStatusRequest {
        @NotNull(message = "New status is required")
        private IssueStatus newStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignRequest {
        private Long assigneeId; // null = unassign
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddLabelRequest {
        @NotNull
        private Long labelId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String issueKey;
        private String title;
        private String description;
        private IssueStatus status;
        private IssuePriority priority;
        private LocalDateTime dueDate;
        private Long version;
        private Boolean isPublic;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private UserDTO.SummaryResponse reporter;
        private UserDTO.SummaryResponse assignee;
        private Long projectId;
        private String projectName;
        private String projectKey;
        private Set<LabelDTO.Response> labels;
        private List<ImageDTO.Response> images;
        private Long timeTodoToProgress;
        private Long timeProgressToReview;
        private Long timeReviewToDone;
        private Long timeDoneToReview;
        private Long timeReviewToProgress;
        private Long timeProgressToTodo;
    }
}
