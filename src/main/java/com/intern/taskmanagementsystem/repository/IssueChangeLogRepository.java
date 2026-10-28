package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.IssueChangeLog;
import com.intern.taskmanagementsystem.enums.IssueChangeField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IssueChangeLogRepository extends JpaRepository<IssueChangeLog, Long> {

    /**
     * Tìm mốc thay đổi gần nhất theo fieldName (đặc biệt là STATUS để tính transitionTimeMinutes chính xác).
     */
    Optional<IssueChangeLog> findTopByIssueIdAndFieldNameOrderByIdDesc(Long issueId, IssueChangeField fieldName);

    /**
     * Lấy toàn bộ lịch sử thay đổi của một issue theo thứ tự thời gian.
     * Sử dụng EntityGraph để nạp sẵn thông tin changedBy, tránh LazyInitializationException và N+1.
     */
    @EntityGraph(attributePaths = {"changedBy"})
    List<IssueChangeLog> findByIssueIdOrderByChangedAtAscIdAsc(Long issueId);

    /**
     * Thống kê thời gian trung bình và số lượt theo từng cặp trạng thái (oldValue -> newValue)
     * cho danh sách các projectIds trong khoảng thời gian xác định (chỉ lọc field_name = 'STATUS').
     */
    @Query("SELECT h.oldValue, h.newValue, AVG(h.transitionTimeMinutes), COUNT(h.id) " +
           "FROM IssueChangeLog h " +
           "WHERE h.issue.project.id IN :projectIds " +
           "  AND h.fieldName = com.intern.taskmanagementsystem.enums.IssueChangeField.STATUS " +
           "  AND h.transitionTimeMinutes IS NOT NULL " +
           "  AND (:startDate IS NULL OR h.changedAt >= :startDate) " +
           "GROUP BY h.oldValue, h.newValue")
    List<Object[]> aggregateTransitionsByProjectIds(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate
    );

    /**
     * Thống kê theo tháng cho danh sách các projectIds (phục vụ biểu đồ xu hướng theo tháng).
     */
    @Query(value = "SELECT DATE_FORMAT(h.changed_at, '%Y-%m') AS period, " +
                   "h.old_value, h.new_value, " +
                   "AVG(h.transition_time_minutes) AS avg_minutes, COUNT(h.id) AS cnt " +
                   "FROM issue_change_logs h " +
                   "JOIN issues i ON h.issue_id = i.id " +
                   "WHERE i.project_id IN :projectIds " +
                   "  AND h.field_name = 'STATUS' " +
                   "  AND h.transition_time_minutes IS NOT NULL " +
                   "  AND (:startDate IS NULL OR h.changed_at >= :startDate) " +
                   "  AND (:endDate IS NULL OR h.changed_at <= :endDate) " +
                   "GROUP BY DATE_FORMAT(h.changed_at, '%Y-%m'), h.old_value, h.new_value " +
                   "ORDER BY period ASC", nativeQuery = true)
    List<Object[]> aggregateMonthlyTransitionsByProjectIds(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Thống kê theo từng project cho danh sách các projectIds (phục vụ bảng so sánh các project).
     */
    @Query("SELECT h.issue.project.id, h.oldValue, h.newValue, AVG(h.transitionTimeMinutes), COUNT(h.id) " +
           "FROM IssueChangeLog h " +
           "WHERE h.issue.project.id IN :projectIds " +
           "  AND h.fieldName = com.intern.taskmanagementsystem.enums.IssueChangeField.STATUS " +
           "  AND h.transitionTimeMinutes IS NOT NULL " +
           "  AND (:startDate IS NULL OR h.changedAt >= :startDate) " +
           "GROUP BY h.issue.project.id, h.oldValue, h.newValue")
    List<Object[]> aggregateTransitionsPerProject(
            @Param("projectIds") List<Long> projectIds,
            @Param("startDate") LocalDateTime startDate
    );

    /**
     * Lấy danh sách lịch sử chuyển trạng thái issue có phân trang, hỗ trợ lọc theo dự án, người thực hiện, khoảng ngày, bước chuyển, tìm kiếm và sắp xếp động.
     */
    @EntityGraph(attributePaths = {"issue", "issue.project", "changedBy"})
    @Query(value = "SELECT h FROM IssueChangeLog h " +
           "WHERE h.fieldName = com.intern.taskmanagementsystem.enums.IssueChangeField.STATUS " +
           "  AND h.issue.project.id IN :projectIds " +
           "  AND (:changedById IS NULL OR h.changedBy.id = :changedById) " +
           "  AND (:startDate IS NULL OR h.changedAt >= :startDate) " +
           "  AND (:endDate IS NULL OR h.changedAt <= :endDate) " +
           "  AND (:fromStatus IS NULL OR h.oldValue = :fromStatus) " +
           "  AND (:toStatus IS NULL OR h.newValue = :toStatus) " +
           "  AND (:search IS NULL OR LOWER(h.issue.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(h.issue.issueKey) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(h.id) FROM IssueChangeLog h " +
           "WHERE h.fieldName = com.intern.taskmanagementsystem.enums.IssueChangeField.STATUS " +
           "  AND h.issue.project.id IN :projectIds " +
           "  AND (:changedById IS NULL OR h.changedBy.id = :changedById) " +
           "  AND (:startDate IS NULL OR h.changedAt >= :startDate) " +
           "  AND (:endDate IS NULL OR h.changedAt <= :endDate) " +
           "  AND (:fromStatus IS NULL OR h.oldValue = :fromStatus) " +
           "  AND (:toStatus IS NULL OR h.newValue = :toStatus) " +
           "  AND (:search IS NULL OR LOWER(h.issue.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(h.issue.issueKey) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IssueChangeLog> findTransitionLogs(
            @Param("projectIds") List<Long> projectIds,
            @Param("changedById") Long changedById,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("search") String search,
            Pageable pageable
    );
}
