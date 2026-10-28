package com.intern.taskmanagementsystem.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.WebApplicationContext;

/**
 * Demo kiến thức: bean-scope-bean-lifecycle.
 *
 * 1. @PostConstruct / @PreDestroy — lifecycle hooks trên @Configuration
 * 2. @Scope("request") — prototype-like bean, mỗi HTTP request tạo 1 instance mới
 * 3. @Scope("prototype") — mỗi lần inject tạo 1 instance mới
 */
@Slf4j
@Configuration
public class BeanLifecycleConfig {

    @PostConstruct
    public void onStartup() {
        log.info("[BeanLifecycle] Application started — @PostConstruct called. Bean initialized.");
    }

    @PreDestroy
    public void onShutdown() {
        log.info("[BeanLifecycle] Application shutting down — @PreDestroy called. Cleaning up.");
    }

    /**
     * Request-scoped bean: lưu thông tin người dùng hiện tại trong 1 HTTP request.
     * Mỗi request HTTP sẽ có 1 instance AuditContext riêng — tránh shared state.
     * Demo: @Scope("request") trong ngữ cảnh web.
     */
    @Bean
    @Scope(WebApplicationContext.SCOPE_REQUEST)
    public AuditContext auditContext() {
        return new AuditContext();
    }

    /**
     * Container class cho request-scoped bean — lưu userId của request hiện tại.
     */
    public static class AuditContext {
        private Long currentUserId;
        private String requestId;

        public Long getCurrentUserId() { return currentUserId; }
        public void setCurrentUserId(Long id) { this.currentUserId = id; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String id) { this.requestId = id; }
    }
}
