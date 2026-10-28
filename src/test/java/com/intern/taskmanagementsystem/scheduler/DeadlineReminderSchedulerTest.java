package com.intern.taskmanagementsystem.scheduler;

import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.messaging.IssueEventMessage;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadlineReminderSchedulerTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private DeadlineReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "deadlineReminderHours", 24L);
    }

    @Test
    @DisplayName("Gửi nhắc nhở thành công cho issue có deadline trong vòng 24h tới")
    void testSendDeadlineReminders_success() {
        Project project = Project.builder().key("PRJ").name("Project 1").build();
        project.setId(10L);

        User assignee = User.builder()
                .email("dev@example.com")
                .fullName("Dev User")
                .muteTaskEmails(false)
                .build();
        assignee.setId(1L);

        Issue issue = Issue.builder()
                .issueKey("PRJ-1")
                .title("Urgent Task")
                .project(project)
                .assignee(assignee)
                .dueDate(LocalDateTime.now().plusHours(12))
                .build();
        issue.setId(100L);

        when(issueRepository.findUpcomingDeadlineIssues(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(issue));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(999L);
            return n;
        });

        scheduler.sendDeadlineReminders();

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(issueRepository).findUpcomingDeadlineIssues(nowCaptor.capture(), thresholdCaptor.capture());

        LocalDateTime capturedNow = nowCaptor.getValue();
        LocalDateTime capturedThreshold = thresholdCaptor.getValue();
        assertTrue(capturedThreshold.isAfter(capturedNow), "Threshold phải sau thời điểm hiện tại");
        assertEquals(24, java.time.Duration.between(capturedNow, capturedThreshold).toHours(), "Khoảng cách phải là 24h");

        ArgumentCaptor<IssueEventMessage> messageCaptor = ArgumentCaptor.forClass(IssueEventMessage.class);
        verify(notificationProducer, times(1)).sendNotification(messageCaptor.capture());

        IssueEventMessage sentMessage = messageCaptor.getValue();
        assertEquals(NotificationType.DEADLINE_REMINDER, sentMessage.getType());
        assertEquals("dev@example.com", sentMessage.getRecipientEmail());
        assertEquals("PRJ-1", sentMessage.getIssueKey());
    }

    @Test
    @DisplayName("Bỏ qua issue không có assignee hoặc assignee đã bật muteTaskEmails")
    void testSendDeadlineReminders_skipUnassignedOrMuted() {
        Project project = Project.builder().key("PRJ").name("Project 1").build();
        project.setId(10L);

        User mutedUser = User.builder()
                .email("muted@example.com")
                .fullName("Muted User")
                .muteTaskEmails(true)
                .build();
        mutedUser.setId(2L);

        Issue unassignedIssue = Issue.builder()
                .issueKey("PRJ-2")
                .title("Unassigned Task")
                .project(project)
                .assignee(null)
                .dueDate(LocalDateTime.now().plusHours(5))
                .build();
        unassignedIssue.setId(101L);

        Issue mutedIssue = Issue.builder()
                .issueKey("PRJ-3")
                .title("Muted User Task")
                .project(project)
                .assignee(mutedUser)
                .dueDate(LocalDateTime.now().plusHours(8))
                .build();
        mutedIssue.setId(102L);

        when(issueRepository.findUpcomingDeadlineIssues(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(unassignedIssue, mutedIssue));

        scheduler.sendDeadlineReminders();

        verify(notificationRepository, never()).save(any());
        verify(notificationProducer, never()).sendNotification(any());
    }
}

