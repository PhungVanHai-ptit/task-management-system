package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.dto.ReportDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.IssuePriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findByIssueKey(String issueKey);

    // Lấy issue theo project, filter nhiều điều kiện (JPQL)
    @Query("""
            SELECT i FROM Issue i
            WHERE i.project.id = :projectId
              AND (:status IS NULL OR i.status = :status)
              AND (:priority IS NULL OR i.priority = :priority)
              AND (:assigneeId IS NULL OR i.assignee.id = :assigneeId)
              AND (:labelId IS NULL OR EXISTS (SELECT 1 FROM i.labels l WHERE l.id = :labelId))
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.issueKey) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:isOverdue IS NULL OR :isOverdue = FALSE OR (i.dueDate IS NOT NULL AND i.dueDate < :now AND i.status <> com.intern.taskmanagementsystem.enums.IssueStatus.DONE))
            """)
    Page<Issue> findByProjectWithFilters(
            @Param("projectId") Long projectId,
            @Param("status") IssueStatus status,
            @Param("priority") IssuePriority priority,
            @Param("assigneeId") Long assigneeId,
            @Param("labelId") Long labelId,
            @Param("keyword") String keyword,
            @Param("isOverdue") Boolean isOverdue,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    default Page<Issue> findByProjectWithFilters(
            Long projectId,
            IssueStatus status,
            IssuePriority priority,
            Long assigneeId,
            Long labelId,
            String keyword,
            Pageable pageable
    ) {
        return findByProjectWithFilters(projectId, status, priority, assigneeId, labelId, keyword, null, null, pageable);
    }

    @Query("""
            SELECT DISTINCT i FROM Issue i
            LEFT JOIN FETCH i.reporter
            LEFT JOIN FETCH i.assignee
            LEFT JOIN FETCH i.project
            LEFT JOIN FETCH i.labels
            WHERE i.project.id = :projectId
              AND (:status IS NULL OR i.status = :status)
              AND (:priority IS NULL OR i.priority = :priority)
              AND (:assigneeId IS NULL OR i.assignee.id = :assigneeId)
              AND (:labelId IS NULL OR EXISTS (SELECT 1 FROM i.labels l WHERE l.id = :labelId))
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(i.issueKey) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:isOverdue IS NULL OR :isOverdue = FALSE OR (i.dueDate IS NOT NULL AND i.dueDate < :now AND i.status <> com.intern.taskmanagementsystem.enums.IssueStatus.DONE))
            ORDER BY i.id DESC
            """)
    List<Issue> findAllByProjectWithFilters(
            @Param("projectId") Long projectId,
            @Param("status") IssueStatus status,
            @Param("priority") IssuePriority priority,
            @Param("assigneeId") Long assigneeId,
            @Param("labelId") Long labelId,
            @Param("keyword") String keyword,
            @Param("isOverdue") Boolean isOverdue,
            @Param("now") LocalDateTime now
    );

    default List<Issue> findAllByProjectWithFilters(
            Long projectId,
            IssueStatus status,
            IssuePriority priority,
            Long assigneeId,
            Long labelId,
            String keyword
    ) {
        return findAllByProjectWithFilters(projectId, status, priority, assigneeId, labelId, keyword, null, null);
    }

    // Đếm issue theo từng status trong 1 project (cho dashboard)
    @Query("SELECT i.status, COUNT(i) FROM Issue i WHERE i.project.id = :projectId GROUP BY i.status")
    List<Object[]> countByStatusForProject(@Param("projectId") Long projectId);

    // Đếm issue theo từng status cho danh sách các project (constructor expression, hỗ trợ startDate tùy chọn)
    @Query("""
            SELECT new com.intern.taskmanagementsystem.dto.ReportDTO$ProjectStatusCount(
                i.project.id, i.status, COUNT(i)
            )
            FROM Issue i
            WHERE i.project.id IN :projectIds
              AND (:startDate IS NULL OR i.createdAt >= :startDate)
            GROUP BY i.project.id, i.status
            """)
    List<ReportDTO.ProjectStatusCount> countIssuesByProjectAndStatus(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate
    );

    default List<ReportDTO.ProjectStatusCount> countIssuesByProjectAndStatus(List<Long> projectIds) {
        return countIssuesByProjectAndStatus(projectIds, null);
    }

    // Issue sắp đến hạn (từ hiện tại đến <= threshold, chưa DONE) — dùng cho scheduler
    @Query("SELECT i FROM Issue i WHERE i.dueDate IS NOT NULL AND i.dueDate >= :now AND i.dueDate <= :threshold AND i.status <> com.intern.taskmanagementsystem.enums.IssueStatus.DONE")
    List<Issue> findUpcomingDeadlineIssues(@Param("now") LocalDateTime now, @Param("threshold") LocalDateTime threshold);

    default List<Issue> findUpcomingDeadlineIssues(LocalDateTime threshold) {
        return findUpcomingDeadlineIssues(LocalDateTime.now(), threshold);
    }

    // Native query: top N user có nhiều issue overdue nhất
    @Query(value = """
            SELECT u.id, u.full_name, u.email, COUNT(i.id) AS overdue_count
            FROM issues i
            JOIN users u ON i.assignee_id = u.id
            WHERE i.due_date < NOW()
              AND i.status != 'DONE'
            GROUP BY u.id, u.full_name, u.email
            ORDER BY overdue_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopOverdueUsers(@Param("limit") int limit);

    // Issue tạo mới trong khoảng thời gian (cho báo cáo)
    @Query("SELECT i FROM Issue i WHERE i.project.id = :projectId AND i.createdAt BETWEEN :from AND :to")
    List<Issue> findCreatedBetween(
            @Param("projectId") Long projectId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Issue đóng (DONE) trong khoảng thời gian (for reporting)
    @Query("SELECT i FROM Issue i WHERE i.project.id = :projectId AND i.status = com.intern.taskmanagementsystem.enums.IssueStatus.DONE AND i.updatedAt BETWEEN :from AND :to")
    List<Issue> findClosedBetween(
            @Param("projectId") Long projectId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Đếm issue theo status (JPQL) — kết hợp Collectors.groupingBy trong Service
    List<Issue> findByProjectId(Long projectId);

    // Lấy danh sách issueKey theo prefix để tính toán số thứ tự lớn nhất chính xác theo số học (tránh lỗi sắp xếp chuỗi)
    @Query("SELECT i.issueKey FROM Issue i WHERE i.issueKey LIKE :prefix%")
    List<String> findIssueKeysByPrefix(@Param("prefix") String prefix);
}
