package com.intern.taskmanagementsystem.scheduler;

import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.messaging.IssueEventMessage;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled Job: Định kỳ quét các notification có trạng thái FAILED để gửi bù.
 * - Giới hạn batch: Tối đa 50 bản ghi mỗi đợt quét (tránh tràn bộ nhớ khi mail server chết lâu).
 * - Lọc an toàn: Chỉ quét các thông báo tạo trong 24h gần nhất và chưa quá maxRetries (3 lần).
 * - Giãn cách: Chỉ quét bản ghi có lastAttemptAt cách hiện tại ít nhất failedNotificationIntervalMinutes (30 phút).
 * - Kiểm tra nghiệp vụ: Bỏ qua nếu user bị khóa/xóa mềm/mute, hoặc task đã DONE, hoặc task đổi người được gán.
 * - An toàn giao dịch: Chỉ publish message JMS sau khi Transaction DB đã commit thành công.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailedNotificationResendScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;

    @Value("${app.scheduler.failed-notification-max-retries:3}")
    private int maxRetries;

    @Value("${app.scheduler.failed-notification-interval-minutes:30}")
    private int intervalMinutes;

    @Value("${app.scheduler.failed-notification-batch-size:50}")
    private int batchSize;

    /**
     * Chạy định kỳ (mặc định mỗi 15 phút, cấu hình qua app.scheduler.failed-notification-cron).
     */
    @Scheduled(cron = "${app.scheduler.failed-notification-cron:0 */15 * * * *}")
    @Transactional
    public void resendFailedNotifications() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        LocalDateTime attemptThreshold = LocalDateTime.now().minusMinutes(intervalMinutes);

        List<Notification> failedList = notificationRepository.findFailedNotificationsForRetry(
                NotificationStatus.FAILED,
                NotificationChannel.EMAIL,
                since,
                attemptThreshold,
                maxRetries,
                PageRequest.of(0, batchSize)
        );

        if (failedList.isEmpty()) {
            return;
        }

        log.info("FailedNotificationResendScheduler: found {} failed notifications for resending", failedList.size());

        List<IssueEventMessage> messagesToSend = new ArrayList<>();

        for (Notification notification : failedList) {
            User user = notification.getUser();
            Issue issue = notification.getIssue();

            // 1. Kiểm tra trạng thái User
            if (user == null || user.isLocked() || user.getDeletedAt() != null || user.isMuteTaskEmails()) {
                log.debug("Skipping failed notification id={} because user is invalid, locked, deleted, or muted",
                        notification.getId());
                continue;
            }

            // 2. Kiểm tra tồn tại Issue
            if (issue == null) {
                log.debug("Skipping failed notification id={} because issue is null", notification.getId());
                continue;
            }

            // 3. Với nhắc nhở deadline: nếu issue đã xong (DONE) hoặc đã đổi người thì không nhắc người cũ nữa
            if (NotificationType.DEADLINE_REMINDER.equals(notification.getType())) {
                if (IssueStatus.DONE.equals(issue.getStatus())) {
                    log.info("Skipping deadline reminder for notification id={}: issue [{}] is already DONE",
                            notification.getId(), issue.getIssueKey());
                    continue;
                }
                if (issue.getAssignee() == null || !issue.getAssignee().getId().equals(user.getId())) {
                    log.info("Skipping deadline reminder for notification id={}: assignee of [{}] is no longer user id={}",
                            notification.getId(), issue.getIssueKey(), user.getId());
                    continue;
                }
            }

            // 4. Cập nhật số lần thử và trạng thái trong DB
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setLastAttemptAt(LocalDateTime.now());
            notification.setStatus(NotificationStatus.PENDING);
            notificationRepository.save(notification);

            // 5. Chuẩn bị message để gửi lại vào JMS queue
            IssueEventMessage message = IssueEventMessage.builder()
                    .notificationId(notification.getId())
                    .issueId(issue.getId())
                    .projectId(issue.getProject().getId())
                    .issueKey(issue.getIssueKey())
                    .issueTitle(issue.getTitle())
                    .recipientUserId(user.getId())
                    .recipientEmail(user.getEmail())
                    .recipientName(user.getFullName())
                    .type(notification.getType())
                    .projectKey(issue.getProject().getKey())
                    .build();

            messagesToSend.add(message);
        }

        if (messagesToSend.isEmpty()) {
            return;
        }

        // 6. Đảm bảo message chỉ được đẩy vào JMS sau khi database commit thành công
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (IssueEventMessage msg : messagesToSend) {
                        try {
                            notificationProducer.sendNotification(msg);
                            log.info("Resent failed notification message id={} for issue [{}] to {}",
                                    msg.getNotificationId(), msg.getIssueKey(), msg.getRecipientEmail());
                        } catch (Exception ex) {
                            log.error("Failed to re-publish JMS message for notification id={}: {}",
                                    msg.getNotificationId(), ex.getMessage(), ex);
                        }
                    }
                }
            });
        } else {
            for (IssueEventMessage msg : messagesToSend) {
                notificationProducer.sendNotification(msg);
            }
        }
    }
}
