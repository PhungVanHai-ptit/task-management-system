package com.intern.taskmanagementsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class AuthDTO {

    private AuthDTO() {}

    public record CaptchaResponse(String captchaId, String captchaImage) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "{error.email.required}")
        @Email(message = "{error.email.invalid}")
        private String email;

        @NotBlank(message = "{error.password.required}")
        private String password;

        private String captchaId;
        private String captchaCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank(message = "{error.email.required}")
        @Email(message = "{error.email.invalid}")
        private String email;

        @NotBlank(message = "{error.password.required}")
        @Size(min = 6, message = "{error.password.size}")
        private String password;

        @NotBlank(message = "{error.fullname.required}")
        @Size(max = 100)
        private String fullName;

        @NotBlank(message = "Mã xác thực captchaId là bắt buộc")
        private String captchaId;

        @NotBlank(message = "Vui lòng nhập mã xác thực")
        private String captchaCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String accessToken;
        private String tokenType;
        private Long expiresIn;
        private Long userId;
        private String email;
        private String fullName;
        private String refreshToken;   // Raw refresh token (trả 1 lần cho client lưu lại)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshResponse {
        private String accessToken;
        private String tokenType;
        private Long expiresIn;
        private String refreshToken;   // Token mới sau khi rotate
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "New password must be at least 6 characters")
        private String newPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordRequest {
        @NotBlank(message = "{error.email.required}")
        @Email(message = "{error.email.invalid}")
        private String email;

        @NotBlank(message = "Mã xác thực captchaId là bắt buộc")
        private String captchaId;

        @NotBlank(message = "Vui lòng nhập mã xác thực")
        private String captchaCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank(message = "Token is required")
        private String token;

        @NotBlank(message = "{error.password.required}")
        @Size(min = 6, message = "{error.password.size}")
        private String newPassword;
    }
}
