package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RefreshToken — lưu refresh token (hash SHA-256) để cấp lại access token.
 * Plain text token KHÔNG được lưu trong DB — chỉ lưu hash.
 * Quan hệ N-1 với User (ON DELETE CASCADE ở DB).
 */
@Entity
@Table(name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_rt_user",    columnList = "user_id"),
                @Index(name = "idx_rt_expires", columnList = "expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hex của raw token — không bao giờ lưu plain text */
    @Column(name = "token_hash", length = 64, unique = true, nullable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Thông tin thiết bị / IP (optional, phục vụ hiển thị session) */
    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ----------------------------------------------------------------

    /** Token đã hết hạn chưa? */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * Token còn hợp lệ để cấp access token mới không?
     * Kiểm tra: chưa hết hạn + user không bị khóa + email đã xác thực.
     */
    public boolean isValidFor(User u) {
        return !isExpired() && !u.isLocked() && u.isEmailVerified();
    }
}
