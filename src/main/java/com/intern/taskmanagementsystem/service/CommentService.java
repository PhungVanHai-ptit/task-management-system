package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.CommentDTO;
import com.intern.taskmanagementsystem.entity.Comment;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.CommentMapper;
import com.intern.taskmanagementsystem.repository.CommentRepository;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.repository.ProjectMemberRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final CommentMapper commentMapper;
    private final ImageStorageService imageStorageService;

    @Transactional
    public CommentDTO.Response addComment(Long issueId, CommentDTO.CreateRequest request, Long authorId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", authorId));

        assertMember(issue.getProject().getId(), authorId);

        Comment comment = Comment.builder()
                .issue(issue)
                .author(author)
                .content(request.getContent())
                .build();

        return toCommentResponse(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<CommentDTO.Response> listComments(Long issueId) {
        return listComments(issueId, null);
    }

    @Transactional(readOnly = true)
    public List<CommentDTO.Response> listComments(Long issueId, Long currentUserId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));

        if (!Boolean.TRUE.equals(issue.getIsPublic())) {
            if (currentUserId == null) {
                throw new UnauthorizedActionException("Công việc này đang ở chế độ riêng tư. Vui lòng đăng nhập để xem bình luận.");
            }
            assertMember(issue.getProject().getId(), currentUserId);
        }

        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId)
                .stream()
                .map(this::toCommentResponse)
                .toList();
    }

    @Transactional
    public CommentDTO.Response updateComment(Long commentId, CommentDTO.UpdateRequest request, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        if (!isAuthor) {
            throw new UnauthorizedActionException("Only the comment author can edit this comment");
        }

        comment.setContent(request.getContent());
        return toCommentResponse(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        boolean isPmOrAdmin = isPmOrAdmin(comment.getIssue().getProject().getId(), userId);

        if (!isAuthor && !isPmOrAdmin) {
            throw new UnauthorizedActionException("Only the comment author, PM, or Admin can delete this comment");
        }

        // Xóa ảnh đính kèm comment (cascade thủ công vì không có FK thật)
        imageStorageService.deleteAllByEntity(
                com.intern.taskmanagementsystem.enums.ImageEntityType.COMMENT, commentId);

        commentRepository.delete(comment);
    }

    /**
     * Xóa toàn bộ comments và ảnh đính kèm theo issueId.
     * Dùng cho IssueService khi xóa Issue (cascade mức ứng dụng).
     */
    @Transactional
    public void deleteAllByIssueId(Long issueId) {
        List<Comment> comments = commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
        for (Comment comment : comments) {
            imageStorageService.deleteAllByEntity(
                    com.intern.taskmanagementsystem.enums.ImageEntityType.COMMENT, comment.getId());
        }
        commentRepository.deleteAll(comments);
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole().getName().equals(SystemRole.ADMIN.name()))
                .orElse(false);
    }

    private void assertMember(Long projectId, Long userId) {
        if (isAdmin(userId)) {
            return;
        }
        if (projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
            throw new UnauthorizedActionException("You are not a member of this project");
        }
    }

    private boolean isPmOrAdmin(Long projectId, Long userId) {
        if (isAdmin(userId)) {
            return true;
        }
        return projectMemberRepository
                .findByProjectIdAndUserIdAndRoleName(projectId, userId, ProjectRoleEnum.PROJECT_MANAGER.name())
                .isPresent();
    }

    public CommentDTO.Response toCommentResponse(Comment comment) {
        CommentDTO.Response res = commentMapper.toCommentResponse(comment);
        var images = imageStorageService.findAllByEntity(
                com.intern.taskmanagementsystem.enums.ImageEntityType.COMMENT, comment.getId());
        res.setImages(imageStorageService.toImageResponseList(images));
        return res;
    }
}
