package com.intern.taskmanagementsystem.entity;

import com.intern.taskmanagementsystem.enums.ImageEntityType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Image entity — metadata của ảnh lưu trên disk.
 * Dùng pattern Polymorphic Association: 1 bảng images phục vụ 4 loại entity.
 *
 * ⚠️ QUAN TRỌNG: entity_id KHÔNG có FK thật (vì trỏ tới 4 bảng khác nhau).
 * → Cascade xóa PHẢI được xử lý bằng code trong:
 *   - IssueService.deleteIssue()
 *   - CommentService.deleteComment()
 *   - ProjectService.deleteProject()
 *   - UserService.deleteUser()
 *
 * Thứ tự xóa bắt buộc:
 *   1. Xóa record trong bảng images (trong @Transactional)
 *   2. Xóa entity cha (trong cùng transaction)
 *   3. Xóa file vật lý trên disk (SAU KHI transaction commit)
 */
@Entity
@Table(
    name = "images",
    indexes = {
        @Index(name = "idx_images_entity", columnList = "entity_type, entity_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Loại đối tượng sở hữu ảnh: ISSUE | COMMENT | USER | PROJECT.
     * Kết hợp với entityId để xác định chủ sở hữu.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20, nullable = false)
    private ImageEntityType entityType;

    /**
     * ID của issue / comment / user / project.
     * KHÔNG có FK thật — xem chú thích ở đầu class.
     */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Tên file gốc do user upload — chỉ để hiển thị. */
    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    /**
     * Tên file thực tế lưu trên disk (UUID.ext).
     * Dùng UUID để chống trùng tên và Path Traversal Attack.
     * Ví dụ: "a3f8c2d1-9b47-4e6a-bc23-1234567890ab.png"
     */
    @Column(name = "stored_name", length = 255, nullable = false, unique = true)
    private String storedName;

    /**
     * URL để trình duyệt truy cập ảnh.
     * Ví dụ: "/uploads/issues/a3f8c2d1-....png"
     * Được lưu sẵn để tránh build URL lại mỗi lần query.
     */
    @Column(name = "url", length = 512, nullable = false)
    private String url;

    /** Kích thước file tính bằng bytes. */
    @Column(name = "file_size")
    private Long fileSize;

    /** Kiểu MIME: image/jpeg · image/png · image/webp · image/gif */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /**
     * User đã upload ảnh này.
     * FK thật duy nhất trong entity này.
     * Dùng để kiểm tra quyền xóa ảnh.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
