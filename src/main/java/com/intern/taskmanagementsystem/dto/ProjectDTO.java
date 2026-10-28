package com.intern.taskmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class ProjectDTO {

    private ProjectDTO() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "{error.project.key.required}")
        @Size(min = 2, max = 20, message = "{error.project.key.size}")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "{error.project.key.pattern}")
        private String key;

        @NotBlank(message = "{error.project.name.required}")
        @Size(max = 150)
        private String name;

        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "{error.project.name.required}")
        @Size(max = 150)
        private String name;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String key;
        private String name;
        private String description;
        private String coverImageUrl;
        private LocalDateTime createdAt;
        private UserDTO.Response creator;
        private com.intern.taskmanagementsystem.enums.SelfAssignPolicy selfAssignPolicy;
        private List<MemberResponse> members;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSettingsRequest {
        @NotNull(message = "Chính sách tự gán công việc không được để trống")
        private com.intern.taskmanagementsystem.enums.SelfAssignPolicy selfAssignPolicy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddMemberRequest {
        @NotNull(message = "User ID is required")
        private Long userId;

        @NotNull(message = "Project role ID is required")
        private Long projectRoleId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeMemberRoleRequest {
        @NotNull(message = "Project role ID is required")
        private Long projectRoleId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private UserDTO.SummaryResponse user;
        private String projectRole;
    }
}
