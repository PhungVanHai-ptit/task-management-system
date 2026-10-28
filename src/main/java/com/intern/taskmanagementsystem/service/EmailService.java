package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.messaging.IssueEventMessage;
import com.intern.taskmanagementsystem.messaging.ProjectMemberMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * EmailService: gửi email dùng Thymeleaf template + JavaMailSender.
 * Tách biệt hoàn toàn với tầng REST — chỉ dùng trong NotificationConsumer.
 * Demo kiến thức: emailservice-thymeleaf.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Gửi email xác thực tài khoản khi đăng ký mới.
     */
    public void sendVerificationEmail(String toEmail, String fullName, String token) throws MessagingException {
        Context context = new Context();
        context.setVariable("recipientName", fullName);
        context.setVariable("verificationLink", baseUrl + "/api/auth/verify-email?token=" + token);

        String htmlContent = templateEngine.process("email/account-verification", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("[Mini Jira] Xác thực tài khoản của bạn");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("Verification email sent to {}", toEmail);
    }

    /**
     * Gửi email đặt lại mật khẩu.
     */
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) throws MessagingException {
        Context context = new Context();
        context.setVariable("recipientName", fullName);
        context.setVariable("resetLink", baseUrl + "/reset-password.html?token=" + token);

        String htmlContent = templateEngine.process("email/password-reset", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("[Mini Jira] Yêu cầu đặt lại mật khẩu của bạn");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("Password reset email sent to {}", toEmail);
    }

    /**
     * Gửi email thông báo dựa theo loại notification.
     */
    public void sendNotificationEmail(IssueEventMessage message) throws MessagingException {
        String templateName = resolveTemplateName(message);
        String subject = resolveSubject(message);

        // Build Thymeleaf context
        Context context = new Context();
        context.setVariable("recipientName", message.getRecipientName());
        context.setVariable("issueKey", message.getIssueKey());
        context.setVariable("issueTitle", message.getIssueTitle());
        context.setVariable("projectKey", message.getProjectKey());

        // Đường dẫn trực tiếp đến trang chi tiết issue
        String issueUrl = (message.getProjectId() != null && message.getIssueId() != null)
                ? (baseUrl + "/issue-detail.html?projectId=" + message.getProjectId() + "&issueId=" + message.getIssueId())
                : (baseUrl + "/projects.html");
        context.setVariable("issueUrl", issueUrl);

        // Render HTML template
        String htmlContent = templateEngine.process(templateName, context);

        // Gửi email
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(message.getRecipientEmail());
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("Email sent to {} | template={}", message.getRecipientEmail(), templateName);
    }

    /**
     * Gửi email thông báo khi được thêm vào dự án.
     */
    public void sendProjectMemberAddedEmail(ProjectMemberMessage message) throws MessagingException {
        Context context = new Context();
        context.setVariable("recipientName", message.getRecipientName());
        context.setVariable("projectKey", message.getProjectKey());
        context.setVariable("projectName", message.getProjectName());
        context.setVariable("roleName", message.getRoleName());

        String projectUrl = baseUrl + "/board.html?projectId=" + message.getProjectId();
        context.setVariable("projectUrl", projectUrl);

        String htmlContent = templateEngine.process("email/project-member-added", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(message.getRecipientEmail());
        helper.setSubject("[" + message.getProjectKey() + "] Bạn được thêm vào dự án " + message.getProjectName());
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("Project member added email sent to {}", message.getRecipientEmail());
    }

    private String resolveTemplateName(IssueEventMessage message) {
        return switch (message.getType()) {
            case ISSUE_ASSIGNED    -> "email/issue-assigned";
            case DEADLINE_REMINDER -> "email/deadline-reminder";
            case ISSUE_REASSIGNED  -> "email/issue-reassigned";
        };
    }

    private String resolveSubject(IssueEventMessage message) {
        return switch (message.getType()) {
            case ISSUE_ASSIGNED    -> "[" + message.getIssueKey() + "] Bạn được gán vào issue";
            case DEADLINE_REMINDER -> "[" + message.getIssueKey() + "] Nhắc nhở: deadline sắp đến";
            case ISSUE_REASSIGNED  -> "[" + message.getIssueKey() + "] Công việc đã được chuyển giao cho thành viên khác";
        };
    }
}
