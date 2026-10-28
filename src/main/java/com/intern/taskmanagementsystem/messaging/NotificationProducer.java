package com.intern.taskmanagementsystem.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * JMS Producer — publish message vào notification queue.
 * Demo kiến thức: JMS, bất đồng bộ (async), IoC/DI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final JmsTemplate jmsTemplate;

    @Value("${app.jms.notification-queue:notification.queue}")
    private String notificationQueue;

    @Value("${app.jms.email-verification-queue:email.verification.queue}")
    private String emailVerificationQueue;

    @Value("${app.jms.password-reset-queue:password.reset.queue}")
    private String passwordResetQueue;

    /**
     * Gửi message thông báo vào queue.
     * Gọi sau khi: gán issue, deadline reminder.
     */
    public void sendNotification(IssueEventMessage message) {
        log.info("Publishing notification event to queue [{}]: type={}, issueKey={}, recipient={}",
                notificationQueue, message.getType(), message.getIssueKey(), message.getRecipientEmail());
        jmsTemplate.convertAndSend(notificationQueue, message);
    }

    /**
     * Gửi message xác thực email vào queue.
     * Gọi sau khi: đăng ký tài khoản mới, gửi lại link xác thực.
     */
    public void sendEmailVerification(EmailVerificationMessage message) {
        log.info("Publishing email verification event to queue [{}]: recipient={}",
                emailVerificationQueue, message.getToEmail());
        jmsTemplate.convertAndSend(emailVerificationQueue, message);
    }

    /**
     * Gửi message đặt lại mật khẩu vào queue.
     * Gọi sau khi: yêu cầu quên mật khẩu.
     */
    public void sendPasswordReset(PasswordResetMessage message) {
        log.info("Publishing password reset event to queue [{}]: recipient={}",
                passwordResetQueue, message.getToEmail());
        jmsTemplate.convertAndSend(passwordResetQueue, message);
    }

    @Value("${app.jms.project-member-queue:project.member.queue}")
    private String projectMemberQueue;

    /**
     * Gửi message thông báo thêm thành viên vào dự án.
     * Gọi sau khi: thêm member vào project.
     */
    public void sendProjectMemberAdded(ProjectMemberMessage message) {
        log.info("Publishing project member event to queue [{}]: projectKey={}, recipient={}",
                projectMemberQueue, message.getProjectKey(), message.getRecipientEmail());
        jmsTemplate.convertAndSend(projectMemberQueue, message);
    }
}
