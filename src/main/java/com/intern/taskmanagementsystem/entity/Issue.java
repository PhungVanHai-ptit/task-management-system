package com.intern.taskmanagementsystem.entity;

import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Issue entity — task chính của hệ thống.
 * Quan hệ:
 *  - N-1 Project
 *  - N-1 User (reporter) — ON DELETE RESTRICT ở DB
 *  - N-1 User (assignee, nullable) — ON DELETE SET NULL ở DB
 *  - 1-N Comment (cascade xoá)
 *  - N-N Label qua issue_labels
 */
@Entity
@Table(name = "issues",
        indexes = {
                @Index(name = "idx_issues_status", columnList = "status"),
                @Index(name = "idx_issues_due_date", columnList = "due_date"),
                @Index(name = "idx_issues_assignee", columnList = "assignee_id"),
                @Index(name = "idx_issues_status_due", columnList = "status, due_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue extends BaseEntity {

    @Column(name = "issue_key", length = 20, unique = true, nullable = false)
    private String issueKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private IssueStatus status = IssueStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10, nullable = false)
    @Builder.Default
    private IssuePriority priority = IssuePriority.MEDIUM;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "time_todo_to_progress")
    private Long timeTodoToProgress;

    @Column(name = "time_progress_to_review")
    private Long timeProgressToReview;

    @Column(name = "time_review_to_done")
    private Long timeReviewToDone;

    @Column(name = "time_done_to_review")
    private Long timeDoneToReview;

    @Column(name = "time_review_to_progress")
    private Long timeReviewToProgress;

    @Column(name = "time_progress_to_todo")
    private Long timeProgressToTodo;

    // ----------------------------------------------------------------
    // N-N Label qua issue_labels
    // ----------------------------------------------------------------
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "issue_labels",
            joinColumns = @JoinColumn(name = "issue_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    @Builder.Default
    private Set<Label> labels = new HashSet<>();
}
