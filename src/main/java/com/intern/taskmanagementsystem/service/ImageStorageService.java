package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.config.AppProperties;
import com.intern.taskmanagementsystem.dto.ImageDTO;
import com.intern.taskmanagementsystem.entity.Image;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.ImageEntityType;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.repository.ImageRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * ImageStorageService: quản lý lưu trữ ảnh trên local disk và metadata trong DB.
 *
 * Demo kiến thức:
 *  - TransactionSynchronizationManager: xóa file disk SAU KHI transaction commit
 *  - Validate mime-type và giới hạn số lượng ảnh
 *  - UUID làm tên file để chống Path Traversal Attack
 *  - Polymorphic pattern: 1 service xử lý nhiều loại entity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    // Sub-directory cho từng loại entity trong bảng images
    private static final Map<ImageEntityType, String> SUB_DIRS = Map.of(
            ImageEntityType.ISSUE,   "issues",
            ImageEntityType.COMMENT, "comments"
    );

    // ----------------------------------------------------------------
    // Upload nhiều file cho 1 entity (ISSUE / COMMENT)
    // ----------------------------------------------------------------

    /**
     * Upload nhiều ảnh cùng lúc cho 1 entity.
     * Validate: mime-type, kích thước, giới hạn số ảnh.
     *
     * @param files       danh sách file từ multipart request
     * @param entityType  loại entity (ISSUE / COMMENT)
     * @param entityId    ID của entity
     * @param uploaderId  ID user đang upload
     * @return danh sách Image đã lưu
     */
    @Transactional
    public List<Image> uploadMultiple(List<MultipartFile> files,
                                      ImageEntityType entityType,
                                      Long entityId,
                                      Long uploaderId) {
        User uploader = findUser(uploaderId);

        // Kiểm tra giới hạn số ảnh
        long current = imageRepository.countByEntityTypeAndEntityId(entityType, entityId);
        int limit = getLimit(entityType);
        if (current + files.size() > limit) {
            throw new IllegalArgumentException(
                    String.format("Vượt quá giới hạn ảnh. Hiện có %d, thêm %d, tối đa %d",
                            current, files.size(), limit));
        }

        List<Image> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            saved.add(storeOneFile(file, entityType, entityId, uploader));
        }
        return saved;
    }

    // ----------------------------------------------------------------
    // Quản lý file disk đơn lẻ (Avatar User & Ảnh bìa Project)
    // Lưu ý: Không tạo bản ghi trong bảng images!
    // ----------------------------------------------------------------

    /**
     * Lưu 1 file ảnh trực tiếp lên disk (dành cho avatar user và ảnh bìa project).
     * KHÔNG tạo bản ghi trong bảng images.
     *
     * @param file    file upload từ request
     * @param subDir  thư mục con ("avatars" hoặc "projects")
     * @return URL truy cập file: /uploads/{subDir}/{uuid}.ext
     */
    public String storeDiskFile(MultipartFile file, String subDir) {
        validateMimeType(file);

        if (file.getSize() > appProperties.getUpload().getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "File quá lớn. Tối đa " + appProperties.getUpload().getMaxFileSize() / 1024 / 1024 + " MB");
        }

        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("image");
        String extension = getExtension(originalFilename);
        String storedName = UUID.randomUUID() + extension;

        Path uploadDir = Paths.get(appProperties.getUpload().getDir(), subDir).toAbsolutePath();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục upload: " + uploadDir, e);
        }

        Path filePath = uploadDir.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file: " + storedName, e);
        }

        String url = "/uploads/" + subDir + "/" + storedName;
        log.info("Stored standalone disk file: {} -> {}", storedName, url);
        return url;
    }

    /**
     * Xóa 1 file vật lý trên disk thông qua URL.
     * Dùng cho avatar user và ảnh bìa project (không qua bảng images).
     * Đăng ký xóa sau khi transaction commit nếu đang trong transaction.
     */
    public void deleteDiskFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        String marker = "/uploads/";
        int idx = fileUrl.indexOf(marker);
        if (idx == -1) return;

        String relativePath = fileUrl.substring(idx + marker.length());
        Path filePath = Paths.get(appProperties.getUpload().getDir(), relativePath).toAbsolutePath();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        boolean deleted = Files.deleteIfExists(filePath);
                        if (deleted) {
                            log.info("Deleted disk file after commit: {}", filePath);
                        }
                    } catch (IOException e) {
                        log.error("Failed to delete disk file: {}", filePath, e);
                    }
                }
            });
        } else {
            try {
                Files.deleteIfExists(filePath);
                log.info("Deleted disk file immediately: {}", filePath);
            } catch (IOException e) {
                log.error("Failed to delete disk file: {}", filePath, e);
            }
        }
    }

    // ----------------------------------------------------------------
    // Xóa 1 ảnh theo imageId (check quyền)
    // ----------------------------------------------------------------

    /**
     * Xóa 1 ảnh. Chỉ người upload hoặc Admin mới được xóa.
     * File vật lý được xóa SAU KHI transaction commit thành công.
     */
    @Transactional
    public void deleteOne(Long imageId, Long requesterId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image", imageId));

        boolean isOwner = image.getUploadedBy().getId().equals(requesterId);
        boolean isAdmin = isAdmin(requesterId);
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedActionException("Bạn không có quyền xóa ảnh này");
        }

        String storedName = image.getStoredName();
        ImageEntityType entityType = image.getEntityType();

        imageRepository.delete(image);

        // Xóa file vật lý sau khi transaction commit
        registerFileDeleteAfterCommit(List.of(storedName), entityType);
    }

    // ----------------------------------------------------------------
    // Xóa TẤT CẢ ảnh của 1 entity (gọi trước khi xóa entity cha)
    // ----------------------------------------------------------------

    /**
     * Xóa tất cả ảnh của 1 entity.
     * GỌI TRƯỚC KHI XÓA ENTITY CHA trong cùng @Transactional.
     *
     * Ví dụ trong IssueService.deleteIssue():
     *   imageStorageService.deleteAllByEntity(ISSUE, issueId);
     *   issueRepository.delete(issue);
     */
    @Transactional
    public void deleteAllByEntity(ImageEntityType entityType, Long entityId) {
        List<Image> images = imageRepository.findAllByEntityTypeAndEntityId(entityType, entityId);
        if (images.isEmpty()) return;

        List<String> storedNames = images.stream().map(Image::getStoredName).toList();
        imageRepository.deleteAllByEntityTypeAndEntityId(entityType, entityId);

        // Xóa file vật lý sau khi transaction commit
        registerFileDeleteAfterCommit(storedNames, entityType);
        log.info("Scheduled deletion of {} image file(s) for {}:{}", storedNames.size(), entityType, entityId);
    }

    // ----------------------------------------------------------------
    // Query
    // ----------------------------------------------------------------

    /** Lấy tất cả ảnh của 1 entity, dùng để include vào DTO response. */
    @Transactional(readOnly = true)
    public List<Image> findAllByEntity(ImageEntityType entityType, Long entityId) {
        return imageRepository.findAllByEntityTypeAndEntityId(entityType, entityId);
    }

    // ----------------------------------------------------------------
    // Chuyển Image entity → ImageDTO.Response
    // ----------------------------------------------------------------

    public ImageDTO.Response toImageResponse(Image image) {
        return ImageDTO.Response.builder()
                .id(image.getId())
                .fileName(image.getFileName())
                .url(image.getUrl())
                .fileSize(image.getFileSize())
                .mimeType(image.getMimeType())
                .createdAt(image.getCreatedAt())
                .uploadedBy(com.intern.taskmanagementsystem.dto.UserDTO.SummaryResponse.builder()
                        .id(image.getUploadedBy().getId())
                        .email(image.getUploadedBy().getEmail())
                        .fullName(image.getUploadedBy().getFullName())
                        .build())
                .build();
    }

    public List<ImageDTO.Response> toImageResponseList(List<Image> images) {
        return images.stream().map(this::toImageResponse).toList();
    }

    // ================================================================
    // Private helpers
    // ================================================================

    /**
     * Validate và lưu 1 file lên disk + tạo record trong DB.
     */
    private Image storeOneFile(MultipartFile file,
                               ImageEntityType entityType,
                               Long entityId,
                               User uploader) {
        // 1. Validate mime-type
        validateMimeType(file);

        // 2. Validate kích thước
        if (file.getSize() > appProperties.getUpload().getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "File quá lớn. Tối đa " + appProperties.getUpload().getMaxFileSize() / 1024 / 1024 + " MB");
        }

        // 3. Tạo tên file UUID để lưu trên disk
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("image");
        String extension = getExtension(originalFilename);
        String storedName = UUID.randomUUID() + extension;

        // 4. Xác định sub-directory và tạo nếu chưa tồn tại
        String subDir = SUB_DIRS.get(entityType);
        Path uploadDir = Paths.get(appProperties.getUpload().getDir(), subDir).toAbsolutePath();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục upload: " + uploadDir, e);
        }

        // 5. Lưu file lên disk
        Path filePath = uploadDir.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file: " + storedName, e);
        }

        // 6. Tạo URL truy cập
        String url = "/uploads/" + subDir + "/" + storedName;

        // 7. Lưu metadata vào DB
        Image image = Image.builder()
                .entityType(entityType)
                .entityId(entityId)
                .fileName(originalFilename)
                .storedName(storedName)
                .url(url)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedBy(uploader)
                .build();

        Image saved = imageRepository.save(image);
        log.info("Stored image: {} for {}:{}", storedName, entityType, entityId);
        return saved;
    }

    /**
     * Đăng ký callback xóa file disk sau khi transaction commit thành công.
     *
     * Dùng TransactionSynchronizationManager — pattern chuẩn Spring.
     * Đảm bảo: nếu transaction rollback → file không bị xóa.
     */
    private void registerFileDeleteAfterCommit(List<String> storedNames, ImageEntityType entityType) {
        String subDir = SUB_DIRS.get(entityType);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String storedName : storedNames) {
                    Path filePath = Paths.get(appProperties.getUpload().getDir(), subDir, storedName).toAbsolutePath();
                    try {
                        boolean deleted = Files.deleteIfExists(filePath);
                        if (deleted) {
                            log.info("Deleted image file: {}", filePath);
                        } else {
                            log.warn("Image file not found (already deleted?): {}", filePath);
                        }
                    } catch (IOException e) {
                        // Ghi log lỗi nhưng không throw — transaction đã commit rồi
                        log.error("Failed to delete image file: {}", filePath, e);
                    }
                }
            }
        });
    }

    private void validateMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        String allowedTypes = appProperties.getUpload().getAllowedTypes();
        if (contentType == null || !Arrays.asList(allowedTypes.split(",")).contains(contentType)) {
            throw new IllegalArgumentException(
                    "Loại file không hợp lệ: " + contentType + ". Chỉ chấp nhận: " + allowedTypes);
        }
    }

    private int getLimit(ImageEntityType entityType) {
        return switch (entityType) {
            case ISSUE   -> appProperties.getUpload().getMaxImagesPerIssue();
            case COMMENT -> appProperties.getUpload().getMaxImagesPerComment();
        };
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot).toLowerCase() : "";
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> "ADMIN".equals(u.getRole().getName()))
                .orElse(false);
    }
}
