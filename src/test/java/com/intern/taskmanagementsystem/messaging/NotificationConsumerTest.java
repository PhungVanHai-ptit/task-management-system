package com.intern.taskmanagementsystem.messaging;

import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.repository.NotificationRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import com.intern.taskmanagementsystem.service.EmailService;
import jakarta.mail.internet.AddressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationConsumer consumer;

    private IssueEventMessage message;
    private Notification notification;

    @BeforeEach
    void setUp() {
        message = IssueEventMessage.builder()
                .notificationId(10L)
                .issueId(100L)
                .issueKey("PROJ-1")
                .recipientEmail("user@example.com")
                .recipientUserId(1L)
                .type(NotificationType.ISSUE_ASSIGNED)
                .build();

        notification = Notification.builder()
                .id(10L)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .build();
    }

    @Test
    void testIdempotencySkipWhenAlreadySent() throws Exception {
        notification.setStatus(NotificationStatus.SENT);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        consumer.handleNotification(message, 1);

        verify(emailService, never()).sendNotificationEmail(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void testSuccessSendsEmailAndSetsSentStatus() throws Exception {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().muteTaskEmails(false).build()));

        consumer.handleNotification(message, 1);

        verify(emailService).sendNotificationEmail(message);
        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void testPermanentErrorMarksFailedWithoutRethrowing() throws Exception {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().muteTaskEmails(false).build()));
        doThrow(new AddressException("Illegal character in address")).when(emailService).sendNotificationEmail(message);

        // Permanent error should NOT throw exception (ACK to avoid infinite redelivery)
        assertDoesNotThrow(() -> consumer.handleNotification(message, 1));

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertNotNull(notification.getLastAttemptAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void testTransientErrorRethrowsToTriggerArtemisRedelivery() throws Exception {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().muteTaskEmails(false).build()));
        doThrow(new MailSendException("SMTP connection timeout")).when(emailService).sendNotificationEmail(message);

        // Transient error MUST rethrow RuntimeException for Artemis rollback and redelivery
        RuntimeException ex = assertThrows(RuntimeException.class, () -> consumer.handleNotification(message, 1));
        assertTrue(ex.getMessage().contains("Transient mail delivery failure"));

        assertEquals(NotificationStatus.PENDING, notification.getStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void testTransientErrorExhaustedMarksFailedAndRethrowsForDLQ() throws Exception {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().muteTaskEmails(false).build()));
        doThrow(new MailSendException("SMTP connection timeout")).when(emailService).sendNotificationEmail(message);

        // Attempt 4/4 exhausted
        assertThrows(RuntimeException.class, () -> consumer.handleNotification(message, 4));

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        verify(notificationRepository).save(notification);
    }
}
