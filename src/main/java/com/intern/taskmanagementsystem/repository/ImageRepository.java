package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.Image;
import com.intern.taskmanagementsystem.enums.ImageEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository cho bảng images.
 * Các query đều filter theo (entityType, entityId) để tận dụng index.
 */
public interface ImageRepository extends JpaRepository<Image, Long> {

    /**
     * Lấy tất cả ảnh của 1 entity (issue/comment/user/project).
     * Dùng index: idx_images_entity (entity_type, entity_id).
     */
    List<Image> findAllByEntityTypeAndEntityId(ImageEntityType entityType, Long entityId);

    /**
     * Xóa tất cả ảnh của 1 entity khỏi DB.
     * Gọi trước khi xóa entity cha để tránh orphan records.
     * Sau khi transaction commit → xóa file vật lý trên disk.
     */
    void deleteAllByEntityTypeAndEntityId(ImageEntityType entityType, Long entityId);

    /**
     * Đếm số ảnh hiện có của 1 entity — dùng để kiểm tra giới hạn.
     * Ví dụ: issue tối đa 10 ảnh, comment tối đa 5 ảnh.
     */
    long countByEntityTypeAndEntityId(ImageEntityType entityType, Long entityId);
}
