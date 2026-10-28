package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.entity.BlacklistedToken;
import com.intern.taskmanagementsystem.repository.BlacklistedTokenRepository;
import com.intern.taskmanagementsystem.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(blacklistedTokenRepository, jwtTokenProvider, 900000L);
    }

    @Test
    void testIsBlacklistedNullOrBlank() {
        assertFalse(tokenBlacklistService.isBlacklisted(null));
        assertFalse(tokenBlacklistService.isBlacklisted(""));
        assertFalse(tokenBlacklistService.isBlacklisted("   "));
        verifyNoInteractions(blacklistedTokenRepository);
    }

    @Test
    void testIsBlacklistedCacheMissDoesNotQueryDatabase() {
        String randomJti = "unknown-jti-123";
        // Khi không có trong cache, trả về false và TUYỆT ĐỐI không query MySQL
        assertFalse(tokenBlacklistService.isBlacklisted(randomJti));
        verifyNoInteractions(blacklistedTokenRepository);
    }

    @Test
    void testInitLoadsActiveTokensIntoCache() {
        BlacklistedToken token1 = BlacklistedToken.builder()
                .jti("active-jti-1")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        BlacklistedToken token2 = BlacklistedToken.builder()
                .jti("active-jti-2")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(blacklistedTokenRepository.findAllActive(any(LocalDateTime.class)))
                .thenReturn(List.of(token1, token2));

        tokenBlacklistService.init();

        assertTrue(tokenBlacklistService.isBlacklisted("active-jti-1"));
        assertTrue(tokenBlacklistService.isBlacklisted("active-jti-2"));
        assertFalse(tokenBlacklistService.isBlacklisted("active-jti-3"));
    }

    @Test
    void testBlacklistTokenPutsInCacheAndSavesToDb() {
        String rawToken = "sample.jwt.token";
        String jti = "test-jti-xyz";
        Date futureExpiration = new Date(System.currentTimeMillis() + 600_000); // 10 mins in future

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(jti);
        when(claims.getExpiration()).thenReturn(futureExpiration);
        when(jwtTokenProvider.parseClaimsAllowExpired(rawToken)).thenReturn(claims);
        when(blacklistedTokenRepository.existsByJti(jti)).thenReturn(false);

        tokenBlacklistService.blacklistToken(rawToken);

        // Kiểm tra RAM cache nhận diện ngay lập tức
        assertTrue(tokenBlacklistService.isBlacklisted(jti));
        // Kiểm tra DB được ghi nhận
        verify(blacklistedTokenRepository).save(any(BlacklistedToken.class));
    }
}
