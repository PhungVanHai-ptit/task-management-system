package com.intern.taskmanagementsystem.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        service = new CaptchaService();
    }

    @Test
    void testGenerateCaptcha() {
        CaptchaService.CaptchaData data = service.generateCaptcha();
        assertNotNull(data);
        assertNotNull(data.captchaId());
        assertNotNull(data.captchaImage());
        assertTrue(data.captchaImage().startsWith("data:image/png;base64,"));
    }

    @Test
    void testValidateAndConsumeNullOrBlank() {
        assertFalse(service.validateAndConsume(null, "12345"));
        assertFalse(service.validateAndConsume("some-id", null));
        assertFalse(service.validateAndConsume("", "12345"));
        assertFalse(service.validateAndConsume("some-id", ""));
        assertFalse(service.validateAndConsume("   ", "   "));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testValidateAndConsumeWrongCodeConsumesEntry() throws Exception {
        Field cacheField = CaptchaService.class.getDeclaredField("captchaCache");
        cacheField.setAccessible(true);
        Cache<String, String> cache = (Cache<String, String>) cacheField.get(service);

        cache.put("test-id", "ABCDE");

        // Nhập sai mã -> false
        assertFalse(service.validateAndConsume("test-id", "WRONG"));

        // Xác nhận entry đã bị xóa ngay cả khi nhập sai (chống brute force)
        assertNull(cache.getIfPresent("test-id"));

        // Lần thử tiếp theo với mã đúng cũng thất bại vì id đã bị vô hiệu hóa
        assertFalse(service.validateAndConsume("test-id", "ABCDE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testValidateAndConsumeSuccessCaseInsensitive() throws Exception {
        Field cacheField = CaptchaService.class.getDeclaredField("captchaCache");
        cacheField.setAccessible(true);
        Cache<String, String> cache = (Cache<String, String>) cacheField.get(service);

        cache.put("test-id", "AbCdE");

        // Nhập đúng mã (không phân biệt hoa thường) -> true
        assertTrue(service.validateAndConsume("test-id", "abcde"));

        // Đã bị tiêu thụ (consumed) -> gọi lại trả về false
        assertNull(cache.getIfPresent("test-id"));
        assertFalse(service.validateAndConsume("test-id", "abcde"));
    }
}
