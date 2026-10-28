package com.intern.taskmanagementsystem.messaging;

import com.intern.taskmanagementsystem.entity.Notification;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.repository.NotificationRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import com.intern.taskmanagementsystem.service.EmailService;
import jakarta.mail.internet.AddressException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * JMS Consumer — nhận message từ queue, gọi EmailService để gửi email.
 * - Idempotency: Kiểm tra nếu notification đã SENT thì bỏ qua để chống gửi trùng lặp.
 * - Phân loại lỗi:
 *   + Lỗi vĩnh viễn (AddressException, lỗi template, v.v.): Đánh dấu FAILED, ACK message ngay.
 *   + Lỗi tạm thời (MailSendException, timeout, mất kết nối): Ném RuntimeException để JMS rollback session.
 *     ActiveMQ Artemis Broker sẽ tự động redeliver theo backoff (10s, 30s, 60s) mà KHÔNG block worker thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @JmsListener(destination = "${app.jms.notification-queue}")
    public void handleNotification(
            IssueEventMessage message,
            @Header(name = "JMSXDeliveryCount", defaultValue = "1") int deliveryCount
    ) {
        log.info("Received notification event: type={}, issueKey={}, recipient={}, attempt={}/4",
                message.getType(), message.getIssueKey(), message.getRecipientEmail(), deliveryCount);

        // 1. Chống gửi trùng email (Idempotency check)
        if (message.getNotificationId() != null) {
            var existingNotif = notificationRepository.findById(message.getNotificationId());
            if (existingNotif.isPresent() && existingNotif.get().getStatus() == NotificationStatus.SENT) {
                log.info("Notification id={} has already been SENT. Skipping to prevent duplicate email.",
                        message.getNotificationId());
                return;
            }
        }

        // 2. Kiểm tra nếu user đã tắt nhận mail thông báo
        if (message.getRecipientUserId() != null) {
            boolean isMuted = userRepository.findById(message.getRecipientUserId())
                    .map(User::isMuteTaskEmails)
                    .orElse(false);
            if (isMuted) {
                log.info("User {} has muted task emails, skipping sending email for issueKey={}",
                        message.getRecipientEmail(), message.getIssueKey());
                return;
            }
        }

        // 3. Thực hiện gửi email
        try {
            emailService.sendNotificationEmail(message);
            updateNotificationStatus(message.getNotificationId(), NotificationStatus.SENT);
            log.info("Email sent successfully to {} (attempt {})", message.getRecipientEmail(), deliveryCount);
        } catch (Exception ex) {
            boolean transientError = isTransientError(ex);

            if (!transientError) {
                // Lỗi vĩnh viễn (sai địa chỉ mail, lỗi logic template...): Đánh dấu FAILED và ACK, không retry
                log.error("Permanent email error for {}: {}. Marking FAILED without retry.",
                        message.getRecipientEmail(), ex.getMessage(), ex);
                updateNotificationStatus(message.getNotificationId(), NotificationStatus.FAILED);
                return;
            }

            // Lỗi tạm thời (kết nối, timeout, mail server bận):
            if (deliveryCount >= 3) {
                log.error("Exhausted all 3 delivery attempts for notificationId={} to {}. Marking FAILED.",
                        message.getNotificationId(), message.getRecipientEmail());
                updateNotificationStatus(message.getNotificationId(), NotificationStatus.FAILED);
            } else {
                log.warn("Transient mail error for notificationId={} to {} (attempt {}/3): {}. Requesting Artemis redelivery.",
                        message.getNotificationId(), message.getRecipientEmail(), deliveryCount, ex.getMessage());
                updateNotificationStatus(message.getNotificationId(), NotificationStatus.PENDING);
            }

            // Ném RuntimeException để JMS rollback session, kích hoạt Artemis broker redelivery theo backoff
            throw new RuntimeException("Transient mail delivery failure, rollback for Artemis redelivery", ex);
        }
    }

    private void updateNotificationStatus(Long notificationId, NotificationStatus status) {
        if (notificationId == null) return;
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setStatus(status);
            notification.setLastAttemptAt(LocalDateTime.now());
            if (NotificationStatus.SENT.equals(status)) {
                notification.setSentAt(LocalDateTime.now());
            }
            notificationRepository.save(notification);
        });
    }

    /**
     * Xác định lỗi gửi mail có phải lỗi tạm thời có thể retry hay không.
     */
    private boolean isTransientError(Throwable ex) {
        if (ex == null) return false;
        // AddressException (sai format địa chỉ mail) là lỗi vĩnh viễn
        if (ex instanceof AddressException) {
            return false;
        }
        // MailSendException, SocketTimeoutException, ConnectException, MessagingException chung
        if (ex instanceof org.springframework.mail.MailSendException
                || ex instanceof java.net.SocketTimeoutException
                || ex instanceof java.net.ConnectException
                || ex instanceof jakarta.mail.MessagingException) {
            return true;
        }
        if (ex.getCause() != null && ex.getCause() != ex) {
            return isTransientError(ex.getCause());
        }
        return false;
    }
}
