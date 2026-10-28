package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.ImageDTO;
import com.intern.taskmanagementsystem.entity.Image;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.enums.ImageEntityType;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.repository.CommentRepository;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.repository.ProjectMemberRepository;
import com.intern.taskmanagementsystem.repository.ProjectRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ImageUploadController — quản lý upload và xóa ảnh.
 *
 * Endpoints:
 *  POST /api/images/issues/{issueId}        — upload nhiều ảnh cho issue (chỉ PM, Admin, Reporter)
 *  POST /api/images/comments/{commentId}    — upload nhiều ảnh cho comment
 *  POST /api/images/projects/{projectId}    — upload ảnh bìa project (chỉ PM, Admin)
 *  POST /api/users/me/avatar                — upload avatar (thay cũ) [AuthController]
 *  DELETE /api/images/{imageId}             — xóa 1 ảnh bất kỳ (owner/admin)
 *  DELETE /api/images/projects/{id}/cover   — xóa ảnh bìa project (chỉ PM, Admin)
 *  DELETE /api/users/me/avatar              — xóa avatar [AuthController]
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageStorageService imageStorageService;
    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    private boolean isPmOrAdmin(Long projectId, CustomUserDetails userDetails) {
        if (userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        return projectMemberRepository
                .findByProjectIdAndUserIdAndRoleName(projectId, userDetails.getId(), "PROJECT_MANAGER")
                .isPresent();
    }

    // ----------------------------------------------------------------
    // Upload ảnh cho Issue (nhiều file) — chỉ PM, Admin, hoặc Reporter
    // ----------------------------------------------------------------

    @PostMapping(value = "/issues/{issueId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDTO.UploadResponse> uploadIssueImages(
            @PathVariable Long issueId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));

        boolean pmOrAdmin = isPmOrAdmin(issue.getProject().getId(), userDetails);
        boolean isReporter = issue.getReporter() != null && issue.getReporter().getId().equals(userDetails.getId());

        if (!pmOrAdmin && !isReporter) {
            throw new UnauthorizedActionException("Chỉ người tạo issue, Project Manager hoặc Admin mới có quyền tải ảnh đính kèm lên issue");
        }

        List<Image> saved = imageStorageService.uploadMultiple(
                files, ImageEntityType.ISSUE, issueId, userDetails.getId());

        long total = imageStorageService.findAllByEntity(ImageEntityType.ISSUE, issueId).size();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ImageDTO.UploadResponse.builder()
                        .uploaded(imageStorageService.toImageResponseList(saved))
                        .totalImages((int) total)
                        .build()
        );
    }

    // ----------------------------------------------------------------
    // Upload ảnh cho Comment (nhiều file)
    // ----------------------------------------------------------------

    @PostMapping(value = "/comments/{commentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDTO.UploadResponse> uploadCommentImages(
            @PathVariable Long commentId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResourceNotFoundException("Comment", commentId);
        }

        List<Image> saved = imageStorageService.uploadMultiple(
                files, ImageEntityType.COMMENT, commentId, userDetails.getId());

        long total = imageStorageService.findAllByEntity(ImageEntityType.COMMENT, commentId).size();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ImageDTO.UploadResponse.builder()
                        .uploaded(imageStorageService.toImageResponseList(saved))
                        .totalImages((int) total)
                        .build()
        );
    }

    // ----------------------------------------------------------------
    // Upload ảnh bìa Project (1 file — thay thế ảnh cũ) — chỉ PM, Admin
    // ----------------------------------------------------------------

    @PostMapping(value = "/projects/{projectId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<java.util.Map<String, String>> uploadProjectCover(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!isPmOrAdmin(projectId, userDetails)) {
            throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền thay đổi ảnh bìa dự án");
        }

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        // Xóa file ảnh bìa cũ trên disk nếu tồn tại
        if (project.getCoverImageUrl() != null) {
            imageStorageService.deleteDiskFile(project.getCoverImageUrl());
        }

        // Lưu file ảnh bìa mới lên disk (không qua bảng images)
        String newUrl = imageStorageService.storeDiskFile(file, "projects");
        project.setCoverImageUrl(newUrl);
        projectRepository.save(project);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(java.util.Map.of("url", newUrl));
    }

    // ----------------------------------------------------------------
    // Xóa ảnh bìa Project — chỉ PM, Admin
    // ----------------------------------------------------------------

    @DeleteMapping("/projects/{projectId}/cover")
    public ResponseEntity<Void> deleteProjectCover(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!isPmOrAdmin(projectId, userDetails)) {
            throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền xóa ảnh bìa dự án");
        }

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        if (project.getCoverImageUrl() != null) {
            imageStorageService.deleteDiskFile(project.getCoverImageUrl());
            project.setCoverImageUrl(null);
            projectRepository.save(project);
        }

        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------
    // Xóa 1 ảnh bất kỳ (owner / admin)
    // ----------------------------------------------------------------

    /**
     * Xóa 1 ảnh theo imageId.
     * Chỉ người upload (uploaded_by) hoặc ADMIN mới được xóa.
     */
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long imageId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        imageStorageService.deleteOne(imageId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
