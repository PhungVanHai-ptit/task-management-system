package com.intern.taskmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class ImageDTO {

    private ImageDTO() {}

    /**
     * Response trả về khi upload ảnh thành công hoặc khi load danh sách ảnh.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** ID bản ghi trong bảng images — dùng để gọi API xóa ảnh. */
        private Long id;

        /** Tên file gốc do user upload (để hiển thị). */
        private String fileName;

        /** URL để trình duyệt load ảnh: /uploads/{subdir}/{uuid}.ext */
        private String url;

        /** Kích thước file tính bằng bytes. */
        private Long fileSize;

        /** Kiểu MIME: image/jpeg, image/png... */
        private String mimeType;

        /** Thời điểm upload. */
        private LocalDateTime createdAt;

        /** Thông tin người upload. */
        private UserDTO.SummaryResponse uploadedBy;
    }

    /**
     * Response gộp khi upload nhiều file cùng lúc.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadResponse {
        /** Danh sách ảnh vừa upload thành công. */
        private List<Response> uploaded;

        /** Tổng số ảnh hiện có của entity sau khi upload. */
        private int totalImages;
    }
}
