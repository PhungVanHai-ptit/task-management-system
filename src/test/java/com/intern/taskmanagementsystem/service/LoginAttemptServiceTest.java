package com.intern.taskmanagementsystem.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService();
    }

    @Test
    void testEmailThresholdAtFiveAttempts() {
        String ip = "192.168.1.10";
        String email = "user@test.com";

        assertFalse(loginAttemptService.isCaptchaRequired(ip, email));

        for (int i = 1; i <= 4; i++) {
            loginAttemptService.recordFailedAttempt(ip, email);
            assertFalse(loginAttemptService.isCaptchaRequired(ip, email), "Attempt " + i + " should not require captcha yet");
        }

        // 5th attempt
        loginAttemptService.recordFailedAttempt(ip, email);
        assertTrue(loginAttemptService.isCaptchaRequired(ip, email), "5th attempt on email must require captcha");
    }

    @Test
    void testIpThresholdAtTenAttemptsAcrossDifferentEmails() {
        String ip = "203.0.113.5";

        for (int i = 1; i <= 9; i++) {
            String differentEmail = "user" + i + "@test.com";
            loginAttemptService.recordFailedAttempt(ip, differentEmail);
            assertFalse(loginAttemptService.isCaptchaRequired(ip, "another@test.com"), "IP has not reached 10 attempts yet");
        }

        // 10th attempt from same IP
        loginAttemptService.recordFailedAttempt(ip, "user10@test.com");
        assertTrue(loginAttemptService.isCaptchaRequired(ip, "fresh-account@test.com"), "IP reaching 10 attempts must require captcha for all requests");
    }

    @Test
    void testResetEmailDoesNotResetIp() {
        String ip = "10.0.0.99";
        String email = "victim@test.com";

        // Record 5 attempts
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailedAttempt(ip, email);
        }
        assertTrue(loginAttemptService.isCaptchaRequired(ip, email));

        // User authenticates successfully -> reset email
        loginAttemptService.resetEmailAttempts(email);
        assertEquals(0, loginAttemptService.getFailedAttemptsForEmail(email));

        // Email counter reset, but IP counter still holds 5
        assertEquals(5, loginAttemptService.getFailedAttemptsForIp(ip));

        // Another 5 failed attempts from same IP on any email will hit IP threshold 10
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailedAttempt(ip, "other@test.com");
        }
        assertTrue(loginAttemptService.isCaptchaRequired(ip, "brand-new@test.com"));
    }

    @Test
    void testKeyPrefixIsolation() {
        // Test that an email looking like an IP doesn't collide with the IP key
        String trickyString = "127.0.0.1";
        loginAttemptService.recordFailedAttempt(trickyString, trickyString);

        assertEquals(1, loginAttemptService.getFailedAttemptsForIp(trickyString));
        assertEquals(1, loginAttemptService.getFailedAttemptsForEmail(trickyString));

        loginAttemptService.resetEmailAttempts(trickyString);
        assertEquals(0, loginAttemptService.getFailedAttemptsForEmail(trickyString));
        assertEquals(1, loginAttemptService.getFailedAttemptsForIp(trickyString));
    }
}
