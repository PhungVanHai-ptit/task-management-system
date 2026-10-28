package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User entity — tài khoản người dùng hệ thống.
 * Quan hệ:
 *  - N-1 với Role (Spring Security, mỗi user 1 role hệ thống)
 */
@Entity
@Table(name = "users",
        indexes = {
                @Index(name = "idx_users_email", columnList = "email")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "email", length = 150, unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean isLocked = false;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "mute_task_emails", nullable = false)
    @Builder.Default
    private boolean muteTaskEmails = false;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Integer getTokenVersion() {
        return tokenVersion != null ? tokenVersion : 1;
    }

    // ----------------------------------------------------------------
    // Quan hệ N-1 với Role (hệ thống) — mỗi user 1 role
    // ----------------------------------------------------------------
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}
