package com.intern.taskmanagementsystem.messaging;

import com.intern.taskmanagementsystem.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * JMS Consumer — nhận message từ email verification queue, gọi EmailService gửi mail async.
 * Demo kiến thức: JMS @JmsListener, async processing, decoupling architecture.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {

    private final EmailService emailService;

    @JmsListener(destination = "${app.jms.email-verification-queue:email.verification.queue}")
    public void handleEmailVerification(EmailVerificationMessage message) {
        log.info("Received email verification event for recipient={}", message.getToEmail());

        try {
            emailService.sendVerificationEmail(message.getToEmail(), message.getFullName(), message.getToken());
            log.info("Verification email sent successfully to {}", message.getToEmail());
        } catch (Exception ex) {
            log.error("Failed to send verification email to {}: {}", message.getToEmail(), ex.getMessage(), ex);
        }
    }
}
