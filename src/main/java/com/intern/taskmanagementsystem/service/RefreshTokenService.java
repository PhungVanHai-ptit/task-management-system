package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.entity.RefreshToken;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * RefreshTokenService: tạo, xác thực, xoay và thu hồi refresh token.
 *
 * Bảo mật:
 * - Plain text token KHÔNG được lưu trong DB, chỉ lưu SHA-256 hash.
 * - Client nhận raw token 1 lần duy nhất (trong response login/refresh).
 * - Mọi tra cứu trong DB đều dùng hash.
 *
 * Chiến lược hết hạn (cấu hình qua app.refresh-token.sliding-expiry):
 * - true  (Sliding): mỗi lần refresh, token mới có TTL = now + 7 ngày
 * - false (Fixed):   token mới kế thừa expires_at của token cũ (giới hạn session)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-token.expiration-days:7}")
    private long expirationDays;

    @Value("${app.refresh-token.sliding-expiry:true}")
    private boolean slidingExpiry;

    // ----------------------------------------------------------------
    // Tạo token mới
    // ----------------------------------------------------------------

    /**
     * Tạo refresh token cho user sau khi đăng nhập thành công.
     * @return raw token (UUID) để trả về cho client — KHÔNG lưu vào DB
     */
    @Transactional
    public String createToken(User user, String deviceInfo) {
        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now().plusDays(expirationDays))
                .build();

        refreshTokenRepository.save(entity);
        log.debug("Created refresh token for user id={}", user.getId());
        return rawToken;
    }

    // ----------------------------------------------------------------
    // Xác thực & Xoay token
    // ----------------------------------------------------------------

    /**
     * Xác thực raw token và xoay (rotate):
     * 1. Hash raw token → tìm trong DB.
     * 2. Kiểm tra isValidFor(user): chưa hết hạn + user không bị locked + email verified.
     * 3. Xóa token cũ, tạo token mới.
     * 4. Trả về raw token mới để client lưu lại.
     *
     * @throws ResourceNotFoundException nếu token không tồn tại
     * @throws RuntimeException nếu token hết hạn hoặc user bị chặn
     */
    @Transactional
    public String verifyAndRotate(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken old = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token không hợp lệ"));

        User user = old.getUser();

        if (old.isExpired()) {
            refreshTokenRepository.deleteByTokenHash(tokenHash);
            throw new IllegalArgumentException("Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }

        if (user.isLocked()) {
            refreshTokenRepository.deleteAllByUser(user);
            throw new LockedException("Tài khoản của bạn đã bị khóa");
        }

        if (!user.isEmailVerified()) {
            throw new DisabledException("Tài khoản chưa được kích hoạt");
        }

        // Xác định expires_at cho token mới (Sliding vs Fixed)
        LocalDateTime newExpiry = slidingExpiry
                ? LocalDateTime.now().plusDays(expirationDays)   // Sliding
                : old.getExpiresAt();                             // Fixed

        // Xóa token cũ
        refreshTokenRepository.deleteByTokenHash(tokenHash);

        // Tạo token mới
        String newRaw  = UUID.randomUUID().toString();
        String newHash = hashToken(newRaw);

        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newHash)
                .deviceInfo(old.getDeviceInfo())
                .expiresAt(newExpiry)
                .build();

        refreshTokenRepository.save(newToken);
        log.debug("Rotated refresh token for user id={}", user.getId());
        return newRaw;
    }

    // ----------------------------------------------------------------
    // Thu hồi token
    // ----------------------------------------------------------------

    /**
     * Revoke 1 session (logout từ thiết bị hiện tại).
     */
    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        log.debug("Revoked refresh token (hash prefix={}...)", tokenHash.substring(0, 8));
    }

    /**
     * Revoke tất cả session của user (force logout).
     * Gọi khi: lockUser(), deleteUser(), logoutAll().
     */
    @Transactional
    public void revokeAllByUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("Revoked all refresh tokens for user id={}", user.getId());
    }

    // ----------------------------------------------------------------
    // Cleanup job
    // ----------------------------------------------------------------

    /**
     * Tự động xóa token hết hạn mỗi đêm lúc 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
        log.info("Cleaned up expired refresh tokens");
    }

    // ----------------------------------------------------------------
    // Helper: lấy User theo token hash (dùng sau rotate)
    // ----------------------------------------------------------------

    public User getUserByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .map(RefreshToken::getUser)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));
    }

    // ----------------------------------------------------------------
    // Hash helper
    // ----------------------------------------------------------------

    /**
     * Hash raw token bằng SHA-256 → hex string 64 ký tự.
     * Không cần salt vì UUID đã có entropy 122 bit.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
