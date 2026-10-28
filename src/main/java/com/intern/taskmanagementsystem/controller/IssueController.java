package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.ImportResultDTO;
import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.dto.IssueChangeLogDTO;
import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.service.ExcelService;
import com.intern.taskmanagementsystem.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Issue endpoints — /api/projects/{projectId}/issues
 */
@RestController
@RequestMapping("/api/projects/{projectId}/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final ExcelService excelService;

    @PostMapping
    public ResponseEntity<IssueDTO.Response> createIssue(
            @PathVariable Long projectId,
            @Valid @RequestBody IssueDTO.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(issueService.createIssue(projectId, request, userDetails.getId()));
    }

    @GetMapping
    public ResponseEntity<Page<IssueDTO.Response>> listIssues(
            @PathVariable Long projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long labelId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean myOnly,
            @RequestParam(required = false) Boolean isOverdue,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Long effectiveAssigneeId = assigneeId;
        if (Boolean.TRUE.equals(myOnly) && userDetails != null) {
            effectiveAssigneeId = userDetails.getId();
        }
        return ResponseEntity.ok(
                issueService.listIssues(projectId, status, priority, effectiveAssigneeId, labelId, keyword, isOverdue, pageable)
        );
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<IssueDTO.Response> getIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(issueService.getIssue(issueId, userId));
    }

    @PatchMapping("/{issueId}/visibility")
    public ResponseEntity<IssueDTO.Response> updateVisibility(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueDTO.UpdateVisibilityRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.updateIssueVisibility(issueId, request.getIsPublic(), userDetails.getId()));
    }

    @PutMapping("/{issueId}")
    public ResponseEntity<IssueDTO.Response> updateIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueDTO.UpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.updateIssue(issueId, request, userDetails.getId()));
    }

    @PatchMapping("/{issueId}/status")
    public ResponseEntity<IssueDTO.Response> changeStatus(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueDTO.ChangeStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.changeStatus(issueId, request, userDetails.getId()));
    }

    @GetMapping("/{issueId}/change-logs")
    public ResponseEntity<List<IssueChangeLogDTO>> getChangeLogs(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.getChangeLogs(issueId, userDetails.getId()));
    }

    @PatchMapping("/{issueId}/assignee")
    public ResponseEntity<IssueDTO.Response> assignIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @RequestBody IssueDTO.AssignRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.assignIssue(projectId, issueId, request, userDetails.getId()));
    }

    @PostMapping("/{issueId}/assign-to-me")
    public ResponseEntity<IssueDTO.Response> assignToMe(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.assignToMe(projectId, issueId, userDetails.getId()));
    }

    @PostMapping("/{issueId}/unassign-me")
    public ResponseEntity<IssueDTO.Response> unassignMe(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.unassignMe(projectId, issueId, userDetails.getId()));
    }

    @PostMapping("/{issueId}/labels")
    public ResponseEntity<IssueDTO.Response> addLabel(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueDTO.AddLabelRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.addLabel(issueId, request, userDetails.getId()));
    }

    @DeleteMapping("/{issueId}/labels/{labelId}")
    public ResponseEntity<IssueDTO.Response> removeLabel(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @PathVariable Long labelId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(issueService.removeLabel(issueId, labelId, userDetails.getId()));
    }

    @DeleteMapping("/{issueId}")
    public ResponseEntity<Void> deleteIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        issueService.deleteIssue(issueId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Xuất danh sách issue ra file Excel (.xlsx).
     * Hỗ trợ 2 tùy chọn chuẩn Jira: scope="current" (mặc định) hoặc scope="all".
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportIssues(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "current") String scope,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long labelId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean myOnly,
            @RequestParam(required = false) Boolean isOverdue,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long effectiveAssigneeId = assigneeId;
        if (Boolean.TRUE.equals(myOnly) && userDetails != null) {
            effectiveAssigneeId = userDetails.getId();
        }
        byte[] excelBytes = excelService.exportIssues(projectId, scope, status, priority, effectiveAssigneeId, labelId, keyword, isOverdue);
        String filename = String.format("issues-%d-%s.xlsx", projectId, scope.toLowerCase());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * Tải file Excel mẫu chuẩn Jira để nhập liệu issue.
     */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(@PathVariable Long projectId) {
        byte[] templateBytes = excelService.generateIssueTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"issue-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateBytes);
    }

    /**
     * Nhập danh sách issue từ file Excel (.xlsx).
     * Chỉ PM của Project hoặc Admin mới có quyền import.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importIssues(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(excelService.importIssues(projectId, file, userDetails.getId()));
    }
}
