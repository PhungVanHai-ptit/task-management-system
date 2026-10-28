package com.intern.taskmanagementsystem.messaging;

import com.intern.taskmanagementsystem.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * JMS Consumer — nhận message từ password reset queue, gọi EmailService gửi mail async.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetConsumer {

    private final EmailService emailService;

    @JmsListener(destination = "${app.jms.password-reset-queue:password.reset.queue}")
    public void handlePasswordReset(PasswordResetMessage message) {
        log.info("Received password reset event for recipient={}", message.getToEmail());

        try {
            emailService.sendPasswordResetEmail(message.getToEmail(), message.getFullName(), message.getToken());
            log.info("Password reset email sent successfully to {}", message.getToEmail());
        } catch (Exception ex) {
            log.error("Failed to send password reset email to {}: {}", message.getToEmail(), ex.getMessage(), ex);
        }
    }
}
