package com.intern.taskmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class UserDTO {

    private UserDTO() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
        private String fullName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateNotificationSettingsRequest {
        @NotNull(message = "Giá trị muteTaskEmails không được để trống")
        private Boolean muteTaskEmails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignRolesRequest {
        @NotNull
        @NotEmpty
        private String roleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String email;
        private String fullName;
        private String avatarUrl;
        private boolean locked;
        private boolean emailVerified;
        private boolean muteTaskEmails;
        private LocalDateTime createdAt;
        private String role;
    }

    /** Phiên bản gọn của UserDTO.Response — dùng trong các DTO khác (IssueDTO.Response, CommentDTO.Response...) */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryResponse {
        private Long id;
        private String email;
        private String fullName;
        private String avatarUrl;
        private boolean locked;
    }
}
