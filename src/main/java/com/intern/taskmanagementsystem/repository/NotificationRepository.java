package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByIdDesc(Long userId);

    List<Notification> findByIssueId(Long issueId);

    /**
     * Tìm danh sách các thông báo FAILED cần được Job quét và gửi bù:
     * - Status = :status (FAILED)
     * - Channel = :channel (EMAIL)
     * - n.issue IS NOT NULL (chỉ dành cho thông báo issue)
     * - n.createdAt >= :since (trong 24h gần nhất)
     * - n.retryCount < :maxRetries (tối đa maxRetries lần quét bù)
     * - (n.lastAttemptAt IS NULL OR n.lastAttemptAt <= :attemptThreshold) (giãn cách giữa các lần thử)
     */
    @Query("""
        SELECT n FROM Notification n
        JOIN FETCH n.user u
        JOIN FETCH n.issue i
        WHERE n.status = :status
          AND n.channel = :channel
          AND n.issue IS NOT NULL
          AND n.createdAt >= :since
          AND n.retryCount < :maxRetries
          AND (n.lastAttemptAt IS NULL OR n.lastAttemptAt <= :attemptThreshold)
        ORDER BY n.createdAt ASC
    """)
    List<Notification> findFailedNotificationsForRetry(
            @Param("status") NotificationStatus status,
            @Param("channel") NotificationChannel channel,
            @Param("since") LocalDateTime since,
            @Param("attemptThreshold") LocalDateTime attemptThreshold,
            @Param("maxRetries") int maxRetries,
            Pageable pageable
    );
}
