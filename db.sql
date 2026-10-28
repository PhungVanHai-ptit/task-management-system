-- =============================================================
-- Mini Jira – Database Schema (MySQL)
-- Chạy script này để khởi tạo database từ đầu
-- =============================================================

CREATE DATABASE IF NOT EXISTS mini_jira
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mini_jira;

-- =============================================================
-- 1. roles (hệ thống — Spring Security)
-- =============================================================
CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 2. users
-- =============================================================
CREATE TABLE IF NOT EXISTS users (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    email              VARCHAR(150)   NOT NULL,
    password_hash      VARCHAR(255)   NOT NULL,
    full_name          VARCHAR(100)   NOT NULL,
    is_locked          BOOLEAN        NOT NULL DEFAULT FALSE,
    email_verified     BOOLEAN        NOT NULL DEFAULT FALSE,
    mute_task_emails   BOOLEAN        NOT NULL DEFAULT FALSE,
    role_id            BIGINT         NOT NULL DEFAULT 2,
    deleted_at         DATETIME       NULL,
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_email (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- (Bảng user_roles đã được loại bỏ, sử dụng role_id trong users)
-- =============================================================

-- =============================================================
-- 4. project_roles (role trong ngữ cảnh project — tách biệt)
-- =============================================================
CREATE TABLE IF NOT EXISTS project_roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 5. projects
-- =============================================================
CREATE TABLE IF NOT EXISTS projects (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    `key`       VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_key (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 6. project_members (N-N với cột phụ: project_role_id)
-- =============================================================
CREATE TABLE IF NOT EXISTS project_members (
    project_id      BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    project_role_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project      FOREIGN KEY (project_id)      REFERENCES projects(id)      ON DELETE CASCADE,
    CONSTRAINT fk_pm_user         FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_pm_project_role FOREIGN KEY (project_role_id) REFERENCES project_roles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 7. labels
-- =============================================================
CREATE TABLE IF NOT EXISTS labels (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_labels_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 8. issues
-- =============================================================
CREATE TABLE IF NOT EXISTS issues (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    issue_key   VARCHAR(20)  NOT NULL,
    project_id  BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    assignee_id BIGINT       NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority    VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    due_date    DATETIME     NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    time_todo_to_progress   BIGINT NULL,
    time_progress_to_review BIGINT NULL,
    time_review_to_done     BIGINT NULL,
    time_done_to_review     BIGINT NULL,
    time_review_to_progress BIGINT NULL,
    time_progress_to_todo   BIGINT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issues_key (issue_key),
    INDEX idx_issues_status   (status),
    INDEX idx_issues_due_date (due_date),
    INDEX idx_issues_assignee (assignee_id),
    INDEX idx_issues_status_due (status, due_date),
    CONSTRAINT fk_issues_project  FOREIGN KEY (project_id)  REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_issues_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)    ON DELETE RESTRICT,
    CONSTRAINT fk_issues_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 9. issue_labels (N-N)
-- =============================================================
CREATE TABLE IF NOT EXISTS issue_labels (
    issue_id BIGINT NOT NULL,
    label_id BIGINT NOT NULL,
    PRIMARY KEY (issue_id, label_id),
    CONSTRAINT fk_il_issue FOREIGN KEY (issue_id) REFERENCES issues(id) ON DELETE CASCADE,
    CONSTRAINT fk_il_label FOREIGN KEY (label_id) REFERENCES labels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 10. comments
-- =============================================================
CREATE TABLE IF NOT EXISTS comments (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    issue_id   BIGINT NOT NULL,
    author_id  BIGINT NOT NULL,
    content    TEXT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_comments_issue (issue_id),
    CONSTRAINT fk_comments_issue  FOREIGN KEY (issue_id)  REFERENCES issues(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id)  ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 11. notifications
-- =============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    issue_id        BIGINT      NOT NULL,
    type            VARCHAR(30) NOT NULL,
    channel         VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    sent_at         DATETIME    NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count     INT         NOT NULL DEFAULT 0,
    last_attempt_at DATETIME    NULL,
    PRIMARY KEY (id),
    INDEX idx_notif_user (user_id),
    INDEX idx_notif_retry (status, channel, created_at, retry_count),
    CONSTRAINT fk_notif_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_notif_issue FOREIGN KEY (issue_id) REFERENCES issues(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 12. verification_tokens (xác thực email)
-- =============================================================
CREATE TABLE IF NOT EXISTS verification_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(36)  NOT NULL,
    expiry_date DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vt_token (token),
    INDEX idx_vt_user (user_id),
    INDEX idx_vt_expiry (expiry_date),
    CONSTRAINT fk_vt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 13. password_reset_tokens (đặt lại mật khẩu)
-- =============================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(36)  NOT NULL,
    expiry_date DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prt_token (token),
    INDEX idx_prt_user (user_id),
    INDEX idx_prt_expiry (expiry_date),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- 14. issue_change_logs (lịch sử thay đổi / audit log của công việc)
-- =============================================================
CREATE TABLE IF NOT EXISTS issue_change_logs (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    issue_id                BIGINT       NOT NULL,
    changed_by              BIGINT       NULL,
    field_name              VARCHAR(50)  NOT NULL DEFAULT 'STATUS',
    old_value               VARCHAR(1000) NULL,
    new_value               VARCHAR(1000) NULL,
    transition_time_minutes BIGINT       NULL,
    changed_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_icl_issue_field (issue_id, field_name),
    INDEX idx_icl_issue_changed (issue_id, changed_at),
    CONSTRAINT fk_icl_issue FOREIGN KEY (issue_id) REFERENCES issues(id) ON DELETE CASCADE,
    CONSTRAINT fk_icl_user  FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- Seed data: roles hệ thống
-- =============================================================
INSERT IGNORE INTO roles (name) VALUES ('ADMIN'), ('USER');

-- Seed data: project roles
INSERT IGNORE INTO project_roles (name)
VALUES ('PROJECT_MANAGER'), ('DEVELOPER'), ('REPORTER'), ('TESTER'), ('BA');

-- Seed data: label mẫu
INSERT IGNORE INTO labels (name) VALUES ('bug'), ('feature'), ('improvement'), ('documentation'), ('hotfix');
