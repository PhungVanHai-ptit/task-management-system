package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * VerificationToken — lưu token xác thực email, TTL 24 giờ.
 * Quan hệ N-1 với User (ON DELETE CASCADE ở DB).
 */
@Entity
@Table(name = "verification_tokens",
        indexes = {
                @Index(name = "idx_vt_user",   columnList = "user_id"),
                @Index(name = "idx_vt_expiry", columnList = "expiry_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", length = 36, unique = true, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Kiểm tra token có còn hạn hay không.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
