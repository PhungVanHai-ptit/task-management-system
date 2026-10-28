package com.intern.taskmanagementsystem.dto;

import com.intern.taskmanagementsystem.enums.IssueStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class ReportDTO {

    private ReportDTO() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueActivityResponse {
        private String period; // "2025-W01" hoặc "2025-01"
        private Long created;
        private Long closed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueStatusCountResponse {
        private IssueStatus status;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverdueUserResponse {
        private Long userId;
        private String fullName;
        private String email;
        private Long overdueCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectStatusCount {
        private Long projectId;
        private IssueStatus status;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectDistributionResponse {
        private Long projectId;
        private String projectKey;
        private String projectName;
        private long todoCount;
        private long inProgressCount;
        private long reviewCount;
        private long doneCount;
        private long totalCount;
        private double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardSummaryResponse {
        private long totalProjects;
        private long totalTodo;
        private long totalInProgress;
        private long totalReview;
        private long totalDone;
        private long totalIssues;
        private java.util.List<ProjectDistributionResponse> chartProjects;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionMetric {
        private Double avgMinutes;
        private Double avgSeconds;
        private String formattedTime;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionSummaryResponse {
        private Long selectedProjectId;
        private String selectedProjectName;

        // 6 chỉ số của dự án được chọn (hoặc toàn bộ dự án thuộc scope)
        private TransitionMetric todoToProgress;
        private TransitionMetric progressToReview;
        private TransitionMetric reviewToDone;
        private TransitionMetric doneToReview;
        private TransitionMetric reviewToProgress;
        private TransitionMetric progressToTodo;

        // 6 chỉ số Benchmark toàn hệ thống (All Projects)
        private TransitionMetric systemTodoToProgress;
        private TransitionMetric systemProgressToReview;
        private TransitionMetric systemReviewToDone;
        private TransitionMetric systemDoneToReview;
        private TransitionMetric systemReviewToProgress;
        private TransitionMetric systemProgressToTodo;

        private Long totalTransitions;
        private Long totalReworkTransitions;
        private Double reworkRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTransitionTrendResponse {
        private String period; // "2026-05"
        private Double todoToProgressAvg;
        private Double progressToReviewAvg;
        private Double reviewToDoneAvg;
        private Double doneToReviewAvg;
        private Double reviewToProgressAvg;
        private Double progressToTodoAvg;
        private Long transitionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectTransitionStatsResponse {
        private Long projectId;
        private String projectKey;
        private String projectName;
        private TransitionMetric todoToProgress;
        private TransitionMetric progressToReview;
        private TransitionMetric reviewToDone;
        private TransitionMetric doneToReview;
        private TransitionMetric reviewToProgress;
        private TransitionMetric progressToTodo;
        private Long totalTransitions;
        private Long reworkCount;
        private Double reworkRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueTransitionLogResponse {
        private Long id;
        private Long issueId;
        private String issueKey;
        private String issueTitle;
        private Long projectId;
        private String projectKey;
        private String projectName;
        private String fromStatus;
        private String toStatus;
        private Long durationSeconds;
        private String formattedDuration;
        private java.time.LocalDateTime changedAt;
        private UserDTO.SummaryResponse changedBy;
    }
}
