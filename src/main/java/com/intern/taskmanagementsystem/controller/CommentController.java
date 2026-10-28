package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.CommentDTO;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Comment endpoints — /api/issues/{issueId}/comments
 */
@RestController
@RequestMapping("/api/issues/{issueId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentDTO.Response> addComment(
            @PathVariable Long issueId,
            @Valid @RequestBody CommentDTO.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(issueId, request, userDetails.getId()));
    }

    @GetMapping
    public ResponseEntity<List<CommentDTO.Response>> listComments(
            @PathVariable Long issueId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(commentService.listComments(issueId, userId));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentDTO.Response> updateComment(
            @PathVariable Long issueId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentDTO.UpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(commentService.updateComment(commentId, request, userDetails.getId()));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long issueId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        commentService.deleteComment(commentId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
