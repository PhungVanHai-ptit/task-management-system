package com.intern.taskmanagementsystem.messaging;

import com.intern.taskmanagementsystem.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * JMS Consumer — nhận message từ project member queue, gọi EmailService gửi mail async.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectMemberConsumer {

    private final EmailService emailService;

    @JmsListener(destination = "${app.jms.project-member-queue:project.member.queue}")
    public void handleProjectMemberAdded(ProjectMemberMessage message) {
        log.info("Received project member event: projectKey={}, recipient={}",
                message.getProjectKey(), message.getRecipientEmail());

        try {
            emailService.sendProjectMemberAddedEmail(message);
            log.info("Project member email sent successfully to {}", message.getRecipientEmail());
        } catch (Exception ex) {
            log.error("Failed to send project member email to {}: {}", message.getRecipientEmail(), ex.getMessage(), ex);
        }
    }
}
