package com.intern.taskmanagementsystem.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Quản lý số lần đăng nhập thất bại (Hybrid: theo cả IP và Email).
 * - Email: nhập sai 5 lần bắt buộc nhập Captcha (chống brute-force nhắm vào 1 tài khoản cụ thể).
 * - IP: nhập sai 10 lần bắt buộc nhập Captcha (chống credential stuffing từ 1 IP, ngưỡng cao hơn để tránh ảnh hưởng văn phòng chung NAT).
 * - Khi đăng nhập thành công: CHỈ reset bộ đếm Email, KHÔNG reset bộ đếm IP (để IP tự hết hạn theo thời gian TTL).
 * - Bộ nhớ dùng Caffeine cache với TTL 30 phút và max size 10,000 để chống phình RAM.
 */
@Slf4j
@Service
public class LoginAttemptService {

    public static final int MAX_FAILED_ATTEMPTS_EMAIL = 5;
    public static final int MAX_FAILED_ATTEMPTS_IP = 10;
    private static final Duration EXPIRE_DURATION = Duration.ofMinutes(30);

    private final Cache<String, Integer> attemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(EXPIRE_DURATION)
            .maximumSize(10_000)
            .build();

    private String emailKey(String email) {
        return (email == null || email.isBlank()) ? "" : "email:" + email.trim().toLowerCase();
    }

    private String ipKey(String ip) {
        return (ip == null || ip.isBlank()) ? "" : "ip:" + ip.trim();
    }

    /**
     * Ghi nhận 1 lần đăng nhập thất bại cho cả IP và Email (nếu có).
     * Trả về số lần thất bại hiện tại của email.
     */
    public int recordFailedAttempt(String ip, String email) {
        String iKey = ipKey(ip);
        if (!iKey.isBlank()) {
            int ipAttempts = attemptsCache.asMap().merge(iKey, 1, Integer::sum);
            log.info("Recorded failed login for [{}]: attempts={}", iKey, ipAttempts);
        }

        String eKey = emailKey(email);
        if (!eKey.isBlank()) {
            int emailAttempts = attemptsCache.asMap().merge(eKey, 1, Integer::sum);
            log.info("Recorded failed login for [{}]: attempts={}", eKey, emailAttempts);
            return emailAttempts;
        }

        return 0;
    }

    /**
     * Đăng nhập thành công:
     * CHỈ reset bộ đếm Email (vì người dùng chính chủ đã xác thực thành công).
     * KHÔNG reset bộ đếm IP (để tránh kẻ tấn công có 1 tài khoản hợp lệ lặp lại chiêu sai 4 lần -> đúng 1 lần -> brute-force tiếp).
     */
    public void resetEmailAttempts(String email) {
        String eKey = emailKey(email);
        if (!eKey.isBlank()) {
            attemptsCache.invalidate(eKey);
            log.info("Reset email login attempts for [{}]", eKey);
        }
    }

    public int getFailedAttemptsForEmail(String email) {
        String eKey = emailKey(email);
        if (eKey.isBlank()) return 0;
        Integer count = attemptsCache.getIfPresent(eKey);
        return count != null ? count : 0;
    }

    public int getFailedAttemptsForIp(String ip) {
        String iKey = ipKey(ip);
        if (iKey.isBlank()) return 0;
        Integer count = attemptsCache.getIfPresent(iKey);
        return count != null ? count : 0;
    }

    /**
     * Kiểm tra có bắt buộc nhập captcha không:
     * - Nếu IP sai >= MAX_FAILED_ATTEMPTS_IP (10 lần).
     * - HOẶC nếu email sai >= MAX_FAILED_ATTEMPTS_EMAIL (5 lần).
     */
    public boolean isCaptchaRequired(String ip, String email) {
        if (getFailedAttemptsForIp(ip) >= MAX_FAILED_ATTEMPTS_IP) {
            return true;
        }
        if (email != null && !email.isBlank() && getFailedAttemptsForEmail(email) >= MAX_FAILED_ATTEMPTS_EMAIL) {
            return true;
        }
        return false;
    }
}
