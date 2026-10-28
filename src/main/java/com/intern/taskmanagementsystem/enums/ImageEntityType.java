package com.intern.taskmanagementsystem.enums;

/**
 * Phân loại đối tượng sở hữu ảnh đính kèm (nhiều ảnh) trong bảng images.
 * Chỉ áp dụng cho ISSUE và COMMENT (Polymorphic Association).
 *
 * Lưu ý: Avatar user (avatar_url) và ảnh bìa project (cover_image_url)
 * lưu trực tiếp URL vào bảng users và projects, không tạo bản ghi trong bảng images.
 */
public enum ImageEntityType {
    /** Ảnh đính kèm issue — nhiều ảnh / issue (tối đa 10) */
    ISSUE,

    /** Ảnh đính kèm comment — nhiều ảnh / comment (tối đa 5) */
    COMMENT
}

