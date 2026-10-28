package com.intern.taskmanagementsystem.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.intern.taskmanagementsystem.entity.BlacklistedToken;
import com.intern.taskmanagementsystem.repository.BlacklistedTokenRepository;
import com.intern.taskmanagementsystem.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Service quản lý danh sách đen (Blacklist) cho Access Token khi user đăng xuất.
 * Sử dụng Caffeine Cache (RAM) làm nguồn chân lý chính (Source of Truth) cho hot path xác thực O(1),
 * kết hợp MySQL để lưu trữ bền vững qua các lần restart server.
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // Bộ đệm RAM: jti -> Boolean.TRUE (tra cứu cực nhanh O(1), 0 query DB trên hot path)
    // Không đặt maximumSize (tự giới hạn theo TTL tối đa 15 phút của Access Token)
    private final Cache<String, Boolean> blacklistCache;

    public TokenBlacklistService(
            BlacklistedTokenRepository blacklistedTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.jwt.expiration-ms:900000}") long jwtExpirationMs
    ) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.blacklistCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(jwtExpirationMs))
                .scheduler(Scheduler.systemScheduler())
                .build();
    }

    /**
     * Nạp các token còn hiệu lực từ DB vào RAM khi ứng dụng đã sẵn sàng (ApplicationReadyEvent).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            List<BlacklistedToken> activeTokens = blacklistedTokenRepository.findAllActive(LocalDateTime.now());
            for (BlacklistedToken bt : activeTokens) {
                blacklistCache.put(bt.getJti(), Boolean.TRUE);
            }
            log.info("Initialized token blacklist cache with {} active tokens from DB", activeTokens.size());
        } catch (Exception ex) {
            log.warn("Could not pre-load blacklisted tokens on startup (table might be initializing): {}", ex.getMessage());
        }
    }

    /**
     * Đưa một Access Token vào blacklist.
     * - Parse jti và exp từ token (hỗ trợ kể cả khi token vừa hết hạn).
     * - Lưu ngay vào RAM Cache.
     * - Lưu vào DB để persist qua server restarts (bỏ qua nếu đã tồn tại).
     */
    @Transactional
    public void blacklistToken(String rawAccessToken) {
        if (!StringUtils.hasText(rawAccessToken)) {
            return;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaimsAllowExpired(rawAccessToken);
            if (claims == null) {
                log.debug("Token invalid or unparseable, skipping blacklist");
                return;
            }

            String jti = claims.getId();
            Date expiration = claims.getExpiration();

            if (!StringUtils.hasText(jti) || expiration == null) {
                log.debug("Token does not have jti or expiration claim, skipping blacklist");
                return;
            }

            LocalDateTime expiresAt = expiration.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            // Nếu token đã hết hạn từ lâu trước đó thì không cần lưu
            if (expiresAt.isBefore(LocalDateTime.now())) {
                log.debug("Token already expired, skipping blacklist for jti: {}", jti);
                return;
            }

            // 1. Lưu vào RAM Cache ngay lập tức (O(1))
            blacklistCache.put(jti, Boolean.TRUE);

            // 2. Lưu vào MySQL (tránh duplicate nếu logout nhiều lần cùng token)
            if (!blacklistedTokenRepository.existsByJti(jti)) {
                try {
                    BlacklistedToken record = BlacklistedToken.builder()
                            .jti(jti)
                            .expiresAt(expiresAt)
                            .build();
                    blacklistedTokenRepository.save(record);
                    log.info("Access token blacklisted successfully: jti={}, expiresAt={}", jti, expiresAt);
                } catch (DataIntegrityViolationException ex) {
                    log.debug("Token jti {} already in blacklist DB", jti);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to blacklist access token: {}", ex.getMessage());
        }
    }

    /**
     * Kiểm tra xem một jti có nằm trong blacklist không:
     * - CHỈ kiểm tra RAM Cache (O(1), 0 query DB trên hot path).
     * - Không fallback xuống DB để đảm bảo hiệu năng và tránh lạm dụng DB.
     */
    public boolean isBlacklisted(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        return blacklistCache.getIfPresent(jti) != null;
    }

    /**
     * Job định kỳ dọn dẹp các token đã hết hạn trong MySQL (chạy mỗi 15 phút).
     * RAM Cache tự động được dọn dẹp nhờ Caffeine systemScheduler.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int deletedCount = blacklistedTokenRepository.deleteAllExpiredBefore(now);
            if (deletedCount > 0) {
                log.info("Cleaned up {} expired blacklisted tokens from DB", deletedCount);
            }
        } catch (Exception ex) {
            log.warn("Failed to clean up expired blacklisted tokens from DB: {}", ex.getMessage());
        }
    }
}
