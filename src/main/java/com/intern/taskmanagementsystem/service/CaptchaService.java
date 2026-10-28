package com.intern.taskmanagementsystem.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Service sinh và xác thực mã Captcha ảnh nội bộ.
 * Không phụ thuộc bên thứ ba, chạy hoàn toàn offline & bảo mật.
 */
@Slf4j
@Service
public class CaptchaService {

    // Bảng ký tự dễ nhìn, bỏ các ký tự dễ nhầm lẫn như: 0, O, 1, I, l
    private static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 5;
    private static final int WIDTH = 130;
    private static final int HEIGHT = 42;
    private static final long TTL_SECONDS = 120;

    private final SecureRandom random = new SecureRandom();
    private final Cache<String, String> captchaCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
            .scheduler(Scheduler.systemScheduler())
            .maximumSize(20_000)
            .build();

    public record CaptchaData(String captchaId, String captchaImage) {}

    /**
     * Sinh mã captcha ngẫu nhiên và ảnh Base64 tương ứng.
     */
    public CaptchaData generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = generateRandomCode();

        captchaCache.put(captchaId, code);
        String base64Image = renderCaptchaImage(code);

        return new CaptchaData(captchaId, base64Image);
    }

    /**
     * Xác thực mã captcha và XÓA NGAY (chống tấn công dùng lại / replay attack).
     * Dù người dùng nhập đúng hay sai thì captchaId này cũng bị hủy lập tức.
     */
    public boolean validateAndConsume(String captchaId, String inputCode) {
        if (captchaId == null || captchaId.isBlank() || inputCode == null || inputCode.isBlank()) {
            return false;
        }

        String storedCode = captchaCache.asMap().remove(captchaId.trim());
        if (storedCode == null) {
            log.warn("Captcha ID not found, expired, or already consumed: {}", captchaId);
            return false;
        }

        return storedCode.equalsIgnoreCase(inputCode.trim());
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String renderCaptchaImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Bật khử răng cưa
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Nền sáng nhẹ
        g2d.setColor(new Color(243, 244, 246));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // Viền ngoài nhẹ
        g2d.setColor(new Color(229, 231, 235));
        g2d.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        // Vẽ các đường cong nhiễu (noise lines)
        for (int i = 0; i < 4; i++) {
            g2d.setColor(new Color(180 + random.nextInt(40), 190 + random.nextInt(40), 210 + random.nextInt(30)));
            int x1 = random.nextInt(WIDTH / 4);
            int y1 = random.nextInt(HEIGHT);
            int x2 = WIDTH - random.nextInt(WIDTH / 4);
            int y2 = random.nextInt(HEIGHT);
            g2d.drawLine(x1, y1, x2, y2);
        }

        // Vẽ chấm nhiễu (noise dots)
        for (int i = 0; i < 40; i++) {
            g2d.setColor(new Color(190 + random.nextInt(40), 190 + random.nextInt(40), 200 + random.nextInt(40)));
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            image.setRGB(x, y, g2d.getColor().getRGB());
        }

        // Vẽ từng ký tự với góc xoay ngẫu nhiên
        Font[] fonts = new Font[]{
                new Font(Font.SANS_SERIF, Font.BOLD, 24),
                new Font(Font.SERIF, Font.BOLD, 25),
                new Font(Font.MONOSPACED, Font.BOLD, 24)
        };

        Color[] textColors = new Color[]{
                new Color(15, 23, 42),
                new Color(30, 58, 138),
                new Color(3, 105, 161),
                new Color(67, 56, 202)
        };

        int charWidth = (WIDTH - 24) / CODE_LENGTH;
        for (int i = 0; i < CODE_LENGTH; i++) {
            g2d.setFont(fonts[random.nextInt(fonts.length)]);
            g2d.setColor(textColors[random.nextInt(textColors.length)]);

            AffineTransform original = g2d.getTransform();
            double angle = (random.nextDouble() - 0.5) * 0.45; // xoay nhẹ -12 đến +12 độ
            int x = 12 + i * charWidth + random.nextInt(4);
            int y = 28 + random.nextInt(5);

            g2d.rotate(angle, x, y);
            g2d.drawString(String.valueOf(code.charAt(i)), x, y);
            g2d.setTransform(original);
        }

        g2d.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failed to render captcha image", e);
            throw new IllegalStateException("Không thể tạo ảnh captcha", e);
        }
    }
}
