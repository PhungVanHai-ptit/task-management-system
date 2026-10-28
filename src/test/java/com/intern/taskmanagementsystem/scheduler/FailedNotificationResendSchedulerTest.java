package com.intern.taskmanagementsystem.scheduler;

import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.messaging.IssueEventMessage;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailedNotificationResendSchedulerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private FailedNotificationResendScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "maxRetries", 3);
        ReflectionTestUtils.setField(scheduler, "intervalMinutes", 30);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
    }

    @Test
    void testResendSuccessForEligibleNotification() {
        User user = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .isLocked(false)
                .muteTaskEmails(false)
                .build();
        user.setId(1L);

        Project project = Project.builder()
                .key("PROJ")
                .name("Project Test")
                .build();
        project.setId(10L);

        Issue issue = Issue.builder()
                .issueKey("PROJ-1")
                .title("Issue Title")
                .status(IssueStatus.IN_PROGRESS)
                .project(project)
                .assignee(user)
                .build();
        issue.setId(100L);

        Notification notif = Notification.builder()
                .id(999L)
                .user(user)
                .issue(issue)
                .type(NotificationType.ISSUE_ASSIGNED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.FAILED)
                .retryCount(0)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        when(notificationRepository.findFailedNotificationsForRetry(
                eq(NotificationStatus.FAILED),
                eq(NotificationChannel.EMAIL),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(3),
                any(Pageable.class)
        )).thenReturn(List.of(notif));

        scheduler.resendFailedNotifications();

        assertEquals(1, notif.getRetryCount());
        assertEquals(NotificationStatus.PENDING, notif.getStatus());
        assertNotNull(notif.getLastAttemptAt());
        verify(notificationRepository).save(notif);

        ArgumentCaptor<IssueEventMessage> captor = ArgumentCaptor.forClass(IssueEventMessage.class);
        verify(notificationProducer).sendNotification(captor.capture());
        IssueEventMessage sent = captor.getValue();
        assertEquals(999L, sent.getNotificationId());
        assertEquals("test@example.com", sent.getRecipientEmail());
        assertEquals("PROJ-1", sent.getIssueKey());
    }

    @Test
    void testSkipWhenUserMutedTaskEmails() {
        User mutedUser = User.builder()
                .email("muted@example.com")
                .muteTaskEmails(true)
                .build();
        mutedUser.setId(2L);

        Issue issue = Issue.builder().build();
        issue.setId(101L);

        Notification notif = Notification.builder()
                .id(1000L)
                .user(mutedUser)
                .issue(issue)
                .status(NotificationStatus.FAILED)
                .build();

        when(notificationRepository.findFailedNotificationsForRetry(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(notif));

        scheduler.resendFailedNotifications();

        verify(notificationProducer, never()).sendNotification(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void testSkipDeadlineReminderWhenIssueAlreadyDone() {
        User user = User.builder().email("dev@example.com").build();
        user.setId(3L);

        Issue doneIssue = Issue.builder()
                .issueKey("PROJ-2")
                .status(IssueStatus.DONE)
                .assignee(user)
                .build();
        doneIssue.setId(102L);

        Notification notif = Notification.builder()
                .id(1001L)
                .user(user)
                .issue(doneIssue)
                .type(NotificationType.DEADLINE_REMINDER)
                .status(NotificationStatus.FAILED)
                .build();

        when(notificationRepository.findFailedNotificationsForRetry(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(notif));

        scheduler.resendFailedNotifications();

        verify(notificationProducer, never()).sendNotification(any());
        verify(notificationRepository, never()).save(any());
    }
}
