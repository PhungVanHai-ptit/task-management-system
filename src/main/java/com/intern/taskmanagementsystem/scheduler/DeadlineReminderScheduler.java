package com.intern.taskmanagementsystem.scheduler;

import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.messaging.IssueEventMessage;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cron job: quét issue sắp đến hạn → gửi email nhắc nhở.
 * Chạy hàng ngày lúc 8:00 sáng.
 * Demo kiến thức: @Scheduled, Transactional, JMS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private final IssueRepository issueRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;

    @Value("${app.scheduler.deadline-reminder-hours:24}")
    private long deadlineReminderHours;

    /**
     * Chạy mỗi ngày lúc 8:00 sáng.
     * Tìm issue có due_date nằm trong 24h tới (từ thời điểm hiện tại đến threshold) và chưa DONE → publish reminder.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(deadlineReminderHours);
        List<Issue> upcomingIssues = issueRepository.findUpcomingDeadlineIssues(now, threshold);

        log.info("Deadline reminder scheduler: found {} issues approaching deadline", upcomingIssues.size());

        for (Issue issue : upcomingIssues) {
            if (issue.getAssignee() == null) continue;  // Chỉ nhắc người được gán

            User assignee = issue.getAssignee();
            if (assignee.isMuteTaskEmails()) {
                log.debug("Deadline reminder skipped for issue [{}] → {} (muteTaskEmails is enabled)",
                        issue.getIssueKey(), assignee.getEmail());
                continue;
            }

            // Tạo notification log
            Notification notification = Notification.builder()
                    .user(assignee)
                    .issue(issue)
                    .type(NotificationType.DEADLINE_REMINDER)
                    .channel(NotificationChannel.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .build();
            notification = notificationRepository.save(notification);

            // Publish message → consumer sẽ gửi email
            IssueEventMessage message = IssueEventMessage.builder()
                    .notificationId(notification.getId())
                    .issueId(issue.getId())
                    .projectId(issue.getProject().getId())
                    .issueKey(issue.getIssueKey())
                    .issueTitle(issue.getTitle())
                    .recipientUserId(assignee.getId())
                    .recipientEmail(assignee.getEmail())
                    .recipientName(assignee.getFullName())
                    .type(NotificationType.DEADLINE_REMINDER)
                    .projectKey(issue.getProject().getKey())
                    .build();

            notificationProducer.sendNotification(message);
            log.debug("Deadline reminder queued for issue [{}] → {}", issue.getIssueKey(), assignee.getEmail());
        }
    }
}
