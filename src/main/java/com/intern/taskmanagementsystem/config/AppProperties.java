package com.intern.taskmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bind toàn bộ config của app vào 1 class — type-safe hơn @Value rải rác.
 * Demo kiến thức: spring-property-config (@ConfigurationProperties).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Jms jms = new Jms();
    private Scheduler scheduler = new Scheduler();
    private Upload upload = new Upload();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000L;
    }

    @Data
    public static class Jms {
        private String notificationQueue = "notification.queue";
        private String emailVerificationQueue = "email.verification.queue";
    }

    @Data
    public static class Scheduler {
        private long deadlineReminderHours = 24;
    }

    @Data
    public static class Upload {
        /** Thư mục gốc lưu file (tương đối với CWD hoặc đường dẫn tuyệt đối). */
        private String dir = "uploads";

        /** Kích thước tối đa 1 file upload (bytes). Mặc định 10 MB. */
        private long maxFileSize = 10_485_760L;

        /** Các MIME type được chấp nhận, phân cách bằng dấu phẩy. */
        private String allowedTypes = "image/jpeg,image/png,image/gif,image/webp";

        /** Số ảnh tối đa được đính kèm vào 1 issue. */
        private int maxImagesPerIssue = 10;

        /** Số ảnh tối đa được đính kèm vào 1 comment. */
        private int maxImagesPerComment = 5;
    }
}
