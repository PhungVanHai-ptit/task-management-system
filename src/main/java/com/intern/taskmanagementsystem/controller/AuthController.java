package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.AuthDTO;
import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.service.AuthService;
import com.intern.taskmanagementsystem.service.CaptchaService;
import com.intern.taskmanagementsystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Map;

/**
 * Authentication endpoints — /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final CaptchaService captchaService;
    private final com.intern.taskmanagementsystem.service.LoginAttemptService loginAttemptService;

    /** Sinh mã captcha ảnh nội bộ */
    @GetMapping("/captcha")
    public ResponseEntity<AuthDTO.CaptchaResponse> getCaptcha() {
        CaptchaService.CaptchaData data = captchaService.generateCaptcha();
        return ResponseEntity.ok(new AuthDTO.CaptchaResponse(data.captchaId(), data.captchaImage()));
    }

    /** Kiểm tra xem IP hoặc email này có bị yêu cầu nhập captcha do nhập sai nhiều lần hay không */
    @GetMapping("/captcha-required")
    public ResponseEntity<java.util.Map<String, Object>> checkCaptchaRequired(
            @RequestParam(required = false) String email,
            HttpServletRequest request
    ) {
        String clientIp = request.getRemoteAddr();
        boolean required = loginAttemptService.isCaptchaRequired(clientIp, email);
        int failedAttempts = loginAttemptService.getFailedAttemptsForEmail(email);
        int ipAttempts = loginAttemptService.getFailedAttemptsForIp(clientIp);
        return ResponseEntity.ok(java.util.Map.of(
                "requiresCaptcha", required,
                "failedAttempts", failedAttempts,
                "ipAttempts", ipAttempts
        ));
    }

    /** Đăng ký tài khoản mới — gửi email xác thực */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        if (!captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new IllegalArgumentException("Mã xác thực không chính xác hoặc đã hết hạn");
        }
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Đăng nhập → nhận accessToken + refreshToken.
     * deviceInfo lấy từ User-Agent header (optional).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthDTO.AuthResponse> login(
            @Valid @RequestBody AuthDTO.LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request, clientIp, deviceInfo));
    }

    /**
     * Dùng refresh token để cấp access token mới + xoay refresh token.
     * Không cần JWT (public endpoint).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthDTO.RefreshResponse> refresh(
            @Valid @RequestBody AuthDTO.RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    /**
     * Đăng xuất 1 thiết bị — revoke refresh token hiện tại và blacklist access token (nếu có).
     * Body: { "refreshToken": "..." }
     * Header: Authorization: Bearer <accessToken> (tuỳ chọn)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody AuthDTO.RefreshRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String rawAccessToken = null;
        if (org.springframework.util.StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            rawAccessToken = authHeader.substring(7);
        }
        authService.logout(request.getRefreshToken(), rawAccessToken);
        return ResponseEntity.noContent().build();
    }

    /**
     * Đăng xuất tất cả thiết bị — revoke mọi refresh token của user.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logoutAll(userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Xác thực email qua link trong email.
     * Redirect về login.html với query param kết quả.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        String result = authService.verifyEmail(token);
        URI location = switch (result) {
            case "verified" -> URI.create("/login.html?verified=true");
            case "expired"  -> URI.create("/login.html?verifyError=expired");
            default         -> URI.create("/login.html?verifyError=invalid");
        };
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /**
     * Gửi lại email xác thực — dùng khi link cũ hết hạn.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @RequestParam @NotBlank @Email String email) {
        authService.resendVerification(email);
        return ResponseEntity.noContent().build();
    }

    /**
     * Yêu cầu đặt lại mật khẩu — gửi link xác nhận vào email.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody AuthDTO.ForgotPasswordRequest request) {
        if (!captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new IllegalArgumentException("Mã xác thực không chính xác hoặc đã hết hạn");
        }
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Link đặt lại mật khẩu đã được gửi đến email của bạn."));
    }

    /**
     * Kiểm tra tính hợp lệ của token đặt lại mật khẩu trước khi người dùng nhập form.
     */
    @GetMapping("/validate-reset-token")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(@RequestParam("token") String token) {
        boolean valid = authService.validateResetToken(token);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    /**
     * Đặt lại mật khẩu mới bằng token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody AuthDTO.ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công. Hãy đăng nhập với mật khẩu mới."));
    }

    /** Đổi mật khẩu — cấp cặp token mới cho phiên hiện tại, vô hiệu hóa tất cả phiên khác */
    @PostMapping("/change-password")
    public ResponseEntity<AuthDTO.AuthResponse> changePassword(
            @Valid @RequestBody AuthDTO.ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.changePassword(userDetails.getId(), request, deviceInfo));
    }

    /** Xem thông tin cá nhân */
    @GetMapping("/me")
    public ResponseEntity<UserDTO.Response> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUser(userDetails.getId()));
    }

    /** Cập nhật thông tin cá nhân (đổi tên hiển thị) */
    @PutMapping("/me")
    public ResponseEntity<UserDTO.Response> updateProfile(
            @Valid @RequestBody UserDTO.UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(userService.updateProfile(userDetails.getId(), request));
    }

    /** Cập nhật cài đặt thông báo qua email */
    @PatchMapping("/me/notification-settings")
    public ResponseEntity<UserDTO.Response> updateNotificationSettings(
            @Valid @RequestBody UserDTO.UpdateNotificationSettingsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(userService.updateNotificationSettings(userDetails.getId(), request));
    }

    /**
     * Upload avatar của user hiện tại.
     * Tự động xóa file avatar cũ nếu có.
     * Cập nhật cột avatar_url trong bảng users (không lưu vào bảng images).
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String newUrl = userService.uploadAvatar(userDetails.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", newUrl));
    }

    /**
     * Xóa avatar của user hiện tại — reset về mặc định (null) và xóa file trên disk.
     */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteAvatar(userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
