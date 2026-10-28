package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.dto.IssueChangeLogDTO;
import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.entity.*;
import com.intern.taskmanagementsystem.enums.ImageEntityType;
import com.intern.taskmanagementsystem.enums.IssueChangeField;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.NotificationStatus;
import com.intern.taskmanagementsystem.enums.NotificationChannel;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.enums.SelfAssignPolicy;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.*;
import com.intern.taskmanagementsystem.mapper.IssueChangeLogMapper;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.messaging.IssueEventMessage;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.*;
import com.intern.taskmanagementsystem.util.DateUtils;
import com.intern.taskmanagementsystem.util.IssueKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * IssueService: nghiệp vụ chính của hệ thống.
 * Demo kiến thức:
 *  - synchronized (qua IssueKeyGenerator)
 *  - state machine (lambda/functional interface) cho đổi trạng thái
 *  - @Transactional khi đổi status + tạo notification log cùng lúc
 *  - JMS publish khi gán issue
 *  - MapStruct
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final NotificationRepository notificationRepository;
    private final IssueKeyGenerator issueKeyGenerator;
    private final NotificationProducer notificationProducer;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueMapper issueMapper;
    private final UserMapper userMapper;
    private final ImageStorageService imageStorageService;
    private final CommentService commentService;
    private final IssueChangeLogRepository issueChangeLogRepository;
    private final IssueChangeLogMapper issueChangeLogMapper;

    /**
     * Tạo issue mới trong project — sinh issueKey synchronized.
     * Chỉ Project Manager (người tạo project) hoặc Admin mới được tạo issue.
     */
    @Transactional
    public IssueDTO.Response createIssue(Long projectId, IssueDTO.CreateRequest request, Long reporterId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        assertPmOrAdmin(projectId, reporterId);

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reporterId));

        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee User", request.getAssigneeId()));
            assertProjectMember(projectId, assignee.getId());
            if (assignee.isLocked() || assignee.getDeletedAt() != null) {
                throw new IllegalArgumentException("Không thể giao công việc cho tài khoản đã bị khóa hoặc bị xóa");
            }
            if (!assignee.isEmailVerified()) {
                throw new IllegalArgumentException("Không thể giao công việc cho tài khoản chưa xác thực email");
            }
        }

        if (request.getDueDate() != null && DateUtils.isPast(request.getDueDate())) {
            throw new IllegalArgumentException("Deadline phải lớn hơn hoặc bằng thời gian hiện tại");
        }

        // Sinh issueKey — synchronized tránh race condition
        String issueKey = issueKeyGenerator.generateNextKey(project.getKey());

        Issue issue = Issue.builder()
                .issueKey(issueKey)
                .project(project)
                .reporter(reporter)
                .assignee(assignee)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : IssuePriority.MEDIUM)
                .dueDate(request.getDueDate())
                .status(IssueStatus.TODO)
                .build();

        if (request.getLabelIds() != null && !request.getLabelIds().isEmpty()) {
            List<Label> labels = labelRepository.findAllById(request.getLabelIds());
            issue.getLabels().addAll(labels);
        }

        issue = issueRepository.save(issue);

        // Nếu có assignee, gửi thông báo async qua JMS
        if (assignee != null) {
            publishAssignedNotification(issue, assignee);
        }

        log.info("Created issue [{}] in project [{}]", issueKey, project.getKey());
        return toIssueResponse(issue);
    }

    /**
     * Danh sách issue theo project với filter + phân trang.
     * Demo: Stream filter, Page mapping.
     */
    @Transactional(readOnly = true)
    public Page<IssueDTO.Response> listIssues(Long projectId, IssueStatus status, IssuePriority priority,
                                           Long assigneeId, Long labelId, String keyword, Boolean isOverdue, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return issueRepository.findByProjectWithFilters(projectId, status, priority, assigneeId, labelId, keyword, isOverdue, now, pageable)
                .map(this::toIssueResponse);
    }

    @Transactional(readOnly = true)
    public Page<IssueDTO.Response> listIssues(Long projectId, IssueStatus status, IssuePriority priority,
                                           Long assigneeId, Long labelId, String keyword, Pageable pageable) {
        return listIssues(projectId, status, priority, assigneeId, labelId, keyword, null, pageable);
    }

    /**
     * Xem chi tiết issue.
     * Nếu issue là public: ai có link cũng xem được (kể cả khách vãng lai chưa đăng nhập).
     * Nếu issue là private: bắt buộc đăng nhập và là thành viên project (hoặc Admin).
     */
    @Transactional(readOnly = true)
    public IssueDTO.Response getIssue(Long issueId) {
        return getIssue(issueId, null);
    }

    @Transactional(readOnly = true)
    public IssueDTO.Response getIssue(Long issueId, Long currentUserId) {
        Issue issue = findIssueOrThrow(issueId);

        if (!Boolean.TRUE.equals(issue.getIsPublic())) {
            if (currentUserId == null) {
                throw new UnauthorizedActionException("Công việc này đang ở chế độ riêng tư. Vui lòng đăng nhập để xem.");
            }
            assertMember(issue.getProject().getId(), currentUserId);
        }

        return toIssueResponse(issue);
    }

    /**
     * Cập nhật issue (title, description, priority, dueDate, assignee).
     * Chỉ Project Manager, Admin hoặc người tạo (reporter)/người được giao (assignee) mới có quyền sửa.
     */
    @Transactional
    public IssueDTO.Response updateIssue(Long issueId, IssueDTO.UpdateRequest request, Long userId) {
        Issue issue = findIssueOrThrow(issueId);
        Long projectId = issue.getProject().getId();
        assertMember(projectId, userId);

        boolean isPmOrAdmin = isPmOrAdmin(projectId, userId);
        boolean isReporter = issue.getReporter() != null && issue.getReporter().getId().equals(userId);
        boolean isAssignee = issue.getAssignee() != null && issue.getAssignee().getId().equals(userId);

        if (!isPmOrAdmin && !isReporter && !isAssignee) {
            throw new UnauthorizedActionException("Chỉ Project Manager, Admin hoặc người tạo/người được giao issue mới có quyền chỉnh sửa");
        }

        if (request.getVersion() != null && !request.getVersion().equals(issue.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Issue.class, issueId);
        }

        User actor = (userId != null) ? userRepository.findById(userId).orElse(null) : null;

        if (request.getTitle() != null) {
            String newTitle = request.getTitle().trim();
            String oldTitle = issue.getTitle() != null ? issue.getTitle().trim() : "";
            if (!Objects.equals(oldTitle, newTitle)) {
                if (!isPmOrAdmin) {
                    throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền chỉnh sửa tiêu đề công việc");
                }
                issue.setTitle(newTitle);
                recordChangeLog(issue, IssueChangeField.TITLE, oldTitle, newTitle, null, actor);
            }
        }

        if (request.getDescription() != null) {
            String newDesc = request.getDescription().trim();
            String oldDesc = issue.getDescription() != null ? issue.getDescription().trim() : "";
            if (!Objects.equals(oldDesc, newDesc)) {
                if (!isPmOrAdmin) {
                    throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền chỉnh sửa mô tả công việc");
                }
                issue.setDescription(request.getDescription());
                recordChangeLog(issue, IssueChangeField.DESCRIPTION, null, null, null, actor);
            }
        }

        if (request.getPriority() != null) {
            IssuePriority oldPriority = issue.getPriority();
            IssuePriority newPriority = request.getPriority();
            if (!Objects.equals(oldPriority, newPriority)) {
                if (!isPmOrAdmin) {
                    throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền thay đổi độ ưu tiên của công việc");
                }
                issue.setPriority(newPriority);
                recordChangeLog(issue, IssueChangeField.PRIORITY,
                        oldPriority != null ? oldPriority.name() : null,
                        newPriority.name(),
                        null,
                        actor);
            }
        }

        if (request.getDueDate() != null) {
            LocalDateTime oldDueDate = issue.getDueDate();
            LocalDateTime newDueDate = request.getDueDate();
            boolean isSameDueDate = oldDueDate != null && (
                    oldDueDate.isEqual(newDueDate) ||
                    oldDueDate.toLocalDate().equals(newDueDate.toLocalDate())
            );
            if (!isSameDueDate) {
                if (!isPmOrAdmin) {
                    throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền thay đổi hạn chót (deadline) của công việc");
                }
                if (DateUtils.isPast(newDueDate)) {
                    throw new IllegalArgumentException("Deadline mới phải lớn hơn hoặc bằng thời gian hiện tại");
                }
                issue.setDueDate(newDueDate);
                recordChangeLog(issue, IssueChangeField.DUE_DATE,
                        oldDueDate != null ? oldDueDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null,
                        newDueDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        null,
                        actor);
            }
        }

        // Assignee
        if (Boolean.TRUE.equals(request.getUnassign())) {
            if (issue.getAssignee() != null) {
                validateCanAssign(issue, projectId, null, userId, true);
                changeAssignee(issue, null, actor);
            }
        } else if (request.getAssigneeId() != null) {
            Long currentAssigneeId = issue.getAssignee() != null ? issue.getAssignee().getId() : null;
            if (!request.getAssigneeId().equals(currentAssigneeId)) {
                User assignee = userRepository.findById(request.getAssigneeId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssigneeId()));
                boolean shouldUpdate = validateCanAssign(issue, projectId, assignee, userId, false);
                if (shouldUpdate) {
                    changeAssignee(issue, assignee, actor);
                }
            }
        }

        // Status
        if (request.getStatus() != null && !request.getStatus().equals(issue.getStatus())) {
            IssueStatus from = issue.getStatus();
            IssueStatus to = request.getStatus();

            if (!isReporter && !isPmOrAdmin && !isAssignee) {
                throw new UnauthorizedActionException(
                    "Chỉ người tạo issue, Project Manager hoặc người được giao việc mới có quyền đổi trạng thái"
                );
            }

            if (to == IssueStatus.DONE && !isPmOrAdmin) {
                throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền chuyển trạng thái sang Hoàn thành (DONE)");
            }

            if (to != IssueStatus.DONE && from == IssueStatus.DONE && !isPmOrAdmin) {
                throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền chuyển trạng thái từ trạng thái Hoàn thành (DONE)");
            }

            validateStatusTransition(from, to);
            issue.setStatus(to);
            recordStatusTransition(issue, from, to, userId);
            log.info("Issue [{}] status changed via update: {} → {} by user [{}]", issue.getIssueKey(), from, to, userId);
        }

        // Labels
        if (request.getLabelIds() != null) {
            List<Label> labels = labelRepository.findAllById(request.getLabelIds());
            issue.getLabels().clear();
            issue.getLabels().addAll(labels);
        }

        // Visibility (Chỉ PM, Admin hoặc Reporter mới có quyền đổi. Assignee KHÔNG có quyền)
        if (request.getIsPublic() != null && !request.getIsPublic().equals(issue.getIsPublic())) {
            if (!isPmOrAdmin && !isReporter) {
                throw new UnauthorizedActionException("Chỉ Project Manager, Admin hoặc Người tạo công việc mới có quyền thay đổi trạng thái chia sẻ");
            }
            issue.setIsPublic(request.getIsPublic());
            log.info("Issue [{}] isPublic updated to [{}] via updateIssue by user [{}]", issue.getIssueKey(), issue.getIsPublic(), userId);
        }

        return toIssueResponse(issueRepository.save(issue));
    }

    /**
     * Cập nhật trạng thái công khai (public/private) của issue.
     * Chỉ Project Manager, Admin hoặc Reporter mới có quyền. Người được giao việc (Assignee) KHÔNG có quyền.
     */
    @Transactional
    public IssueDTO.Response updateIssueVisibility(Long issueId, Boolean isPublic, Long userId) {
        if (userId == null) {
            throw new UnauthorizedActionException("Vui lòng đăng nhập để thực hiện thao tác này");
        }
        Issue issue = findIssueOrThrow(issueId);
        Long projectId = issue.getProject().getId();
        assertMember(projectId, userId);

        boolean isPmOrAdmin = isPmOrAdmin(projectId, userId);
        boolean isReporter = issue.getReporter() != null && issue.getReporter().getId().equals(userId);

        if (!isPmOrAdmin && !isReporter) {
            throw new UnauthorizedActionException("Chỉ Project Manager, Admin hoặc Người tạo công việc mới có quyền chia sẻ liên kết này");
        }

        issue.setIsPublic(Boolean.TRUE.equals(isPublic));
        log.info("Issue [{}] visibility set to isPublic={} by user [{}]", issue.getIssueKey(), issue.getIsPublic(), userId);
        return toIssueResponse(issueRepository.save(issue));
    }

    /**
     * Gán / đổi người phụ trách (bởi PM/Admin hoặc chính thành viên theo chính sách).
     * Sau khi gán → publish JMS message → consumer gửi email cho các bên liên quan.
     */
    @Transactional
    public IssueDTO.Response assignIssue(Long projectId, Long issueId, IssueDTO.AssignRequest request, Long requesterId) {
        Issue issue = findIssueOrThrow(issueId);

        // 1. Kiểm tra IDOR: issue phải thuộc về đúng projectId trong URL
        if (!issue.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Công việc không thuộc về dự án này");
        }

        // 2. Không cho phép gán/hủy việc cho issue đã hoàn thành (DONE)
        if (issue.getStatus() == IssueStatus.DONE) {
            throw new IllegalArgumentException("Không thể thay đổi người phụ trách cho công việc đã hoàn thành (DONE)");
        }

        boolean isUnassign = (request.getAssigneeId() == null);
        User targetUser = null;
        if (!isUnassign) {
            targetUser = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssigneeId()));
        }

        boolean shouldUpdate = validateCanAssign(issue, projectId, targetUser, requesterId, isUnassign);
        if (!shouldUpdate) {
            return toIssueResponse(issue); // Idempotent: đã là assignee, trả 200 không cập nhật DB
        }

        User actor = null;
        if (requesterId != null) {
            if (targetUser != null && targetUser.getId().equals(requesterId)) {
                actor = targetUser;
            } else {
                actor = userRepository.findById(requesterId).orElse(null);
            }
        }
        changeAssignee(issue, targetUser, actor);
        issue = issueRepository.save(issue);

        log.info("Issue [{}] assigned to {} by requester id={}", issue.getIssueKey(), targetUser != null ? targetUser.getId() : "null", requesterId);
        return toIssueResponse(issue);
    }

    @Transactional
    public IssueDTO.Response assignIssue(Long issueId, IssueDTO.AssignRequest request, Long requesterId) {
        Issue issue = findIssueOrThrow(issueId);
        return assignIssue(issue.getProject().getId(), issueId, request, requesterId);
    }

    /**
     * Thành viên tự nhận công việc (Assign to me).
     */
    @Transactional
    public IssueDTO.Response assignToMe(Long projectId, Long issueId, Long userId) {
        IssueDTO.AssignRequest request = new IssueDTO.AssignRequest();
        request.setAssigneeId(userId);
        return assignIssue(projectId, issueId, request, userId);
    }

    /**
     * Thành viên hủy nhận việc (Unassign me).
     */
    @Transactional
    public IssueDTO.Response unassignMe(Long projectId, Long issueId, Long userId) {
        IssueDTO.AssignRequest request = new IssueDTO.AssignRequest();
        request.setAssigneeId(null);
        return assignIssue(projectId, issueId, request, userId);
    }

    /**
     * Đổi trạng thái issue theo state machine hợp lệ và phân quyền:
     * - Người tạo (Reporter) hoặc Project Manager / Admin: có toàn quyền đổi trạng thái.
     * - Người được giao (Assignee): chỉ được đổi trạng thái từ TODO sang IN_PROGRESS, và từ IN_PROGRESS sang REVIEW.
     * - Thành viên khác: không có quyền đổi trạng thái.
     */
    @Transactional
    public IssueDTO.Response changeStatus(Long issueId, IssueDTO.ChangeStatusRequest request, Long userId) {
        Issue issue = findIssueOrThrow(issueId);
        Long projectId = issue.getProject().getId();
        assertMember(projectId, userId);

        IssueStatus from = issue.getStatus();
        IssueStatus to = request.getNewStatus();

        if (from == to) {
            return toIssueResponse(issue);
        }

        // 1. Kiểm tra phân quyền người thực hiện:
        // Chỉ người tạo issue, Project Manager, Admin hoặc người được giao việc mới có quyền đổi trạng thái
        boolean isReporter = issue.getReporter() != null && issue.getReporter().getId().equals(userId);
        boolean isPmOrAdmin = isPmOrAdmin(projectId, userId);
        boolean isAssignee = issue.getAssignee() != null && issue.getAssignee().getId().equals(userId);

        if (!isReporter && !isPmOrAdmin && !isAssignee) {
            throw new UnauthorizedActionException(
                "Chỉ người tạo issue, Project Manager hoặc người được giao việc mới có quyền đổi trạng thái"
            );
        }

        // Ràng buộc: Chỉ Project Manager hoặc Admin mới có quyền chuyển trạng thái sang DONE
        if (to == IssueStatus.DONE && !isPmOrAdmin) {
            throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền chuyển trạng thái sang Hoàn thành (DONE)");
        }


        if (to != IssueStatus.DONE && from == IssueStatus.DONE && !isPmOrAdmin) {
            throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền chuyển trạng thái từ trạng thái Hoàn thành (DONE)");
        }

        // 2. Validate trạng thái mới
        validateStatusTransition(from, to);

        // 3. Cập nhật và lưu vào DB
        issue.setStatus(to);
        recordStatusTransition(issue, from, to, userId);
        issueRepository.save(issue);
        log.info("Issue [{}] status changed: {} → {} by user [{}]", issue.getIssueKey(), from, to, userId);

        return toIssueResponse(issue);
    }


    /**
     * Gắn label vào issue.
     */
    @Transactional
    public IssueDTO.Response addLabel(Long issueId, IssueDTO.AddLabelRequest request, Long userId) {
        Issue issue = findIssueOrThrow(issueId);
        assertMember(issue.getProject().getId(), userId);

        Label label = labelRepository.findById(request.getLabelId())
                .orElseThrow(() -> new ResourceNotFoundException("Label", request.getLabelId()));

        issue.getLabels().add(label);
        return toIssueResponse(issueRepository.save(issue));
    }

    /**
     * Gỡ label khỏi issue.
     */
    @Transactional
    public IssueDTO.Response removeLabel(Long issueId, Long labelId, Long userId) {
        Issue issue = findIssueOrThrow(issueId);
        assertMember(issue.getProject().getId(), userId);

        issue.getLabels().removeIf(l -> l.getId().equals(labelId));
        return toIssueResponse(issueRepository.save(issue));
    }

    /**
     * Xoá issue — chỉ Project Manager, Admin hoặc người tạo issue (reporter).
     */
    @Transactional
    public void deleteIssue(Long issueId, Long userId) {
        Issue issue = findIssueOrThrow(issueId);
        Long projectId = issue.getProject().getId();

        boolean isPmOrAdmin = isPmOrAdmin(projectId, userId);
        boolean isReporter = issue.getReporter() != null && issue.getReporter().getId().equals(userId);

        if (!isPmOrAdmin && !isReporter) {
            throw new UnauthorizedActionException("Chỉ Project Manager, Admin hoặc người tạo mới có quyền xóa issue này");
        }

        deleteSingleIssueAndAssociatedData(issue);
        log.info("Deleted issue [{}] by user id={}", issue.getIssueKey(), userId);
    }

    /**
     * Xóa toàn bộ issues và các dữ liệu liên quan (comments, ảnh) theo projectId.
     * Dùng cho ProjectService khi xóa Project (cascade mức ứng dụng).
     */
    @Transactional
    public void deleteAllByProjectId(Long projectId) {
        List<Issue> issues = issueRepository.findByProjectId(projectId);
        for (Issue issue : issues) {
            deleteSingleIssueAndAssociatedData(issue);
        }
    }

    private void deleteSingleIssueAndAssociatedData(Issue issue) {
        Long issueId = issue.getId();
        // 1. Dọn dẹp toàn bộ comments và ảnh của comment qua CommentService
        commentService.deleteAllByIssueId(issueId);

        // 2. Xóa ảnh của issue
        imageStorageService.deleteAllByEntity(ImageEntityType.ISSUE, issueId);

        // 3. Xóa issue
        issueRepository.delete(issue);
    }

    // ================================================================
    // State machine — lambda validator
    // Demo kiến thức: lambda-expressions, functional interface
    // ================================================================

    /**
     * Validate trạng thái chuyển:
     * Cho phép chuyển trạng thái tự do (nhảy cóc) giữa bất kỳ trạng thái nào:
     * TODO, IN_PROGRESS, REVIEW, DONE.
     */
    private void validateStatusTransition(IssueStatus from, IssueStatus to) {
        if (to == null) {
            throw new IllegalArgumentException("Trạng thái mới không được để trống");
        }
        // Cho phép chuyển tự do giữa các trạng thái (TODO, IN_PROGRESS, REVIEW, DONE)
    }

    private static final List<IssueStatus> STATUS_FLOW = List.of(
            IssueStatus.TODO,
            IssueStatus.IN_PROGRESS,
            IssueStatus.REVIEW,
            IssueStatus.DONE
    );

    private record StatusStep(IssueStatus from, IssueStatus to) {}

    /**
     * Ghi nhận lịch sử chuyển trạng thái và tính toán thời lượng giữa các trạng thái.
     * Khi chuyển nhảy cóc (ví dụ: TODO -> DONE, DONE -> TODO,...):
     *  - Phân rã thành chuỗi các bước chuyển trạng thái liền kề.
     *  - Chia đều thời lượng cho các bước.
     *  - Tạo các bản ghi lịch sử tương ứng.
     *  - Cập nhật các biến đo thời gian tương ứng trên Issue.
     */
    private void recordStatusTransition(Issue issue, IssueStatus from, IssueStatus to, Long userId) {
        if (from == to) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Tìm thời điểm bắt đầu ở trạng thái cũ:
        //    Lấy record STATUS gần nhất của issue (lúc issue CHUYỂN SANG trạng thái hiện tại)
        //    Nếu chưa có record nào -> lấy issue.getCreatedAt()
        //    Nếu createdAt là null -> fallback now
        LocalDateTime enteredCurrentStatusAt = issueChangeLogRepository
                .findTopByIssueIdAndFieldNameOrderByIdDesc(issue.getId(), IssueChangeField.STATUS)
                .map(IssueChangeLog::getChangedAt)
                .orElse(issue.getCreatedAt() != null ? issue.getCreatedAt() : now);

        long totalSeconds = Math.max(0, Duration.between(enteredCurrentStatusAt, now).toSeconds());

        // 2. Xác định các bước chuyển trạng thái (hỗ trợ nhảy cóc cả tiến và lùi)
        List<StatusStep> steps = resolveTransitionSteps(from, to);
        int stepCount = steps.size();
        long baseSeconds = stepCount > 0 ? totalSeconds / stepCount : totalSeconds;
        long remainderSeconds = stepCount > 0 ? totalSeconds % stepCount : 0;

        User changedBy = userId != null ? userRepository.findById(userId).orElse(null) : null;
        long accumulatedSeconds = 0;

        for (int i = 0; i < stepCount; i++) {
            StatusStep step = steps.get(i);
            long stepSeconds = baseSeconds + (i == stepCount - 1 ? remainderSeconds : 0);
            accumulatedSeconds += stepSeconds;

            LocalDateTime stepChangedAt;
            if (i == stepCount - 1) {
                stepChangedAt = now;
            } else {
                stepChangedAt = enteredCurrentStatusAt.plusSeconds(accumulatedSeconds);
                if (stepChangedAt.isAfter(now)) {
                    stepChangedAt = now;
                }
            }

            // Ghi log vào bảng issue_change_logs (lưu số giây chuyển đổi)
            IssueChangeLog history = IssueChangeLog.builder()
                    .issue(issue)
                    .changedBy(changedBy)
                    .fieldName(IssueChangeField.STATUS)
                    .oldValue(step.from().name())
                    .newValue(step.to().name())
                    .transitionTimeMinutes(stepSeconds)
                    .changedAt(stepChangedAt)
                    .build();
            issueChangeLogRepository.save(history);

            // Cập nhật biến thời lượng tương ứng trên Issue (tính theo giây)
            updateIssueTransitionDuration(issue, step.from(), step.to(), stepSeconds);
        }
    }

    private List<StatusStep> resolveTransitionSteps(IssueStatus from, IssueStatus to) {
        int fromIdx = STATUS_FLOW.indexOf(from);
        int toIdx = STATUS_FLOW.indexOf(to);

        List<StatusStep> steps = new ArrayList<>();
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) {
            steps.add(new StatusStep(from, to));
            return steps;
        }

        if (fromIdx < toIdx) {
            for (int i = fromIdx; i < toIdx; i++) {
                steps.add(new StatusStep(STATUS_FLOW.get(i), STATUS_FLOW.get(i + 1)));
            }
        } else {
            for (int i = fromIdx; i > toIdx; i--) {
                steps.add(new StatusStep(STATUS_FLOW.get(i), STATUS_FLOW.get(i - 1)));
            }
        }
        return steps;
    }

    private void updateIssueTransitionDuration(Issue issue, IssueStatus from, IssueStatus to, long seconds) {
        if (from == IssueStatus.TODO && to == IssueStatus.IN_PROGRESS) {
            issue.setTimeTodoToProgress(seconds);
        } else if (from == IssueStatus.IN_PROGRESS && to == IssueStatus.REVIEW) {
            issue.setTimeProgressToReview(seconds);
        } else if (from == IssueStatus.REVIEW && to == IssueStatus.DONE) {
            issue.setTimeReviewToDone(seconds);
        } else if (from == IssueStatus.DONE && to == IssueStatus.REVIEW) {
            issue.setTimeDoneToReview(seconds);
        } else if (from == IssueStatus.REVIEW && to == IssueStatus.IN_PROGRESS) {
            issue.setTimeReviewToProgress(seconds);
        } else if (from == IssueStatus.IN_PROGRESS && to == IssueStatus.TODO) {
            issue.setTimeProgressToTodo(seconds);
        }
    }

    /**
     * Ghi nhận 1 bản ghi thay đổi vào issue_change_logs.
     */
    private void recordChangeLog(Issue issue, IssueChangeField field, String oldValue, String newValue, Long minutes, User actor) {
        IssueChangeLog changeLog = IssueChangeLog.builder()
                .issue(issue)
                .changedBy(actor)
                .fieldName(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .transitionTimeMinutes(minutes)
                .build();
        issueChangeLogRepository.save(changeLog);
    }

    /**
     * Điểm duy nhất cập nhật Assignee: kiểm tra thay đổi, gán giá trị, ghi log và gửi thông báo.
     * Tránh bỏ sót log ở các luồng khác nhau và không ghi log nếu không đổi.
     */
    public boolean changeAssignee(Issue issue, User newAssignee, User actor) {
        User oldAssignee = issue.getAssignee();
        Long oldId = oldAssignee != null ? oldAssignee.getId() : null;
        Long newId = newAssignee != null ? newAssignee.getId() : null;

        if (Objects.equals(oldId, newId)) {
            return false;
        }

        issue.setAssignee(newAssignee);
        recordChangeLog(
                issue,
                IssueChangeField.ASSIGNEE,
                oldId != null ? oldId.toString() : null,
                newId != null ? newId.toString() : null,
                null,
                actor
        );

        Long actorId = actor != null ? actor.getId() : null;
        if (oldAssignee != null && !oldAssignee.getId().equals(actorId)) {
            publishAssignedNotification(issue, oldAssignee, NotificationType.ISSUE_REASSIGNED);
        }
        if (newAssignee != null && !newAssignee.getId().equals(actorId)) {
            publishAssignedNotification(issue, newAssignee, NotificationType.ISSUE_ASSIGNED);
        }
        return true;
    }

    /**
     * Lấy toàn bộ lịch sử thay đổi của issue theo thứ tự thời gian.
     * Nạp sẵn thông tin assignee theo batch để tránh N+1.
     */
    @Transactional(readOnly = true)
    public List<IssueChangeLogDTO> getChangeLogs(Long issueId, Long userId) {
        Issue issue = findIssueOrThrow(issueId);
        Long projectId = issue.getProject().getId();
        assertMember(projectId, userId);

        List<IssueChangeLog> logs = issueChangeLogRepository.findByIssueIdOrderByChangedAtAscIdAsc(issueId);
        if (logs.isEmpty()) {
            return List.of();
        }

        // Batch query UserDTO cho các log ASSIGNEE để tránh N+1 query
        Set<Long> assigneeUserIds = new HashSet<>();
        for (IssueChangeLog logItem : logs) {
            if (logItem.getFieldName() == IssueChangeField.ASSIGNEE) {
                addUserIdIfPresent(assigneeUserIds, logItem.getOldValue());
                addUserIdIfPresent(assigneeUserIds, logItem.getNewValue());
            }
        }

        Map<Long, UserDTO.SummaryResponse> userMap = assigneeUserIds.isEmpty() ? Map.of() :
                userRepository.findAllById(assigneeUserIds).stream()
                        .collect(Collectors.toMap(User::getId, userMapper::toUserSummaryResponse));

        return logs.stream().map(logItem -> {
            IssueChangeLogDTO dto = issueChangeLogMapper.toDTO(logItem);
            if (logItem.getFieldName() == IssueChangeField.ASSIGNEE) {
                if (logItem.getOldValue() != null) {
                    dto.setOldAssignee(userMap.get(parseUserId(logItem.getOldValue())));
                }
                if (logItem.getNewValue() != null) {
                    dto.setNewAssignee(userMap.get(parseUserId(logItem.getNewValue())));
                }
            }
            return dto;
        }).toList();
    }

    private void addUserIdIfPresent(Set<Long> userIds, String val) {
        if (val != null && !val.isBlank()) {
            try {
                userIds.add(Long.parseLong(val.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }

    private Long parseUserId(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private Issue findIssueOrThrow(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole() != null && SystemRole.ADMIN.name().equals(u.getRole().getName()))
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

    /**
     * Bắt buộc người dùng phải là thành viên chính thức của dự án (áp dụng cho Assignee, kể cả Admin cũng phải tham gia dự án trước khi được gán việc).
     */
    private void assertProjectMember(Long projectId, Long userId) {
        if (projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
            throw new UnauthorizedActionException("Người dùng phải là thành viên của dự án mới được phân công công việc");
        }
    }

    /**
     * Kiểm tra tính hợp lệ và phân quyền gán / hủy gán công việc.
     * Trả về true nếu cần tiếp tục cập nhật, trả về false nếu idempotent (đã là assignee của issue).
     */
    private boolean validateCanAssign(Issue issue, Long projectId, User targetUser, Long requesterId, boolean isUnassign) {
        // 1. Kiểm tra IDOR: issue phải thuộc về đúng projectId trong URL
        if (!issue.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Công việc không thuộc về dự án này");
        }

        // 2. Không cho phép gán/hủy việc cho issue đã hoàn thành (DONE)
        if (issue.getStatus() == IssueStatus.DONE) {
            throw new IllegalArgumentException("Không thể thay đổi người phụ trách cho công việc đã hoàn thành (DONE)");
        }

        boolean isPmOrAdmin = isPmOrAdmin(projectId, requesterId);
        SelfAssignPolicy policy = issue.getProject().getSelfAssignPolicy() != null
                ? issue.getProject().getSelfAssignPolicy()
                : SelfAssignPolicy.UNASSIGNED_ONLY;

        if (isUnassign) {
            // Hủy gán (Unassign)
            if (isPmOrAdmin) {
                return true;
            }
            // Thành viên thường: chỉ được hủy nếu chính mình đang là assignee
            if (issue.getAssignee() == null || !issue.getAssignee().getId().equals(requesterId)) {
                throw new UnauthorizedActionException("Chỉ người phụ trách hiện tại hoặc Project Manager mới có quyền hủy nhận việc");
            }
            // Không được hủy nếu policy là NONE
            if (policy == SelfAssignPolicy.NONE) {
                throw new UnauthorizedActionException("Dự án hiện không cho phép thành viên tự thao tác nhận/hủy việc");
            }
            return true;
        }

        // Gán cho targetUser
        if (targetUser == null) {
            throw new IllegalArgumentException("Người được gán không hợp lệ");
        }

        // Bắt buộc targetUser phải là thành viên dự án (kể cả Admin cũng phải tham gia dự án trước)
        assertProjectMember(projectId, targetUser.getId());

        if (targetUser.isLocked() || targetUser.getDeletedAt() != null) {
            throw new IllegalArgumentException("Không thể giao công việc cho tài khoản đã bị khóa hoặc bị xóa");
        }

        if (!targetUser.isEmailVerified()) {
            throw new IllegalArgumentException("Không thể giao công việc cho tài khoản chưa xác thực email");
        }

        // Phân quyền:
        if (isPmOrAdmin) {
            return true;
        }

        // Thành viên thường:
        if (!targetUser.getId().equals(requesterId)) {
            throw new UnauthorizedActionException("Thành viên chỉ được tự gán việc cho chính mình");
        }

        // Idempotent: Nếu đã là assignee của issue này rồi thì không làm gì thêm
        if (issue.getAssignee() != null && issue.getAssignee().getId().equals(requesterId)) {
            return false;
        }

        // Nếu issue chưa có người phụ trách (unassigned):
        if (issue.getAssignee() == null) {
            if (policy == SelfAssignPolicy.NONE) {
                throw new UnauthorizedActionException("Dự án hiện không cho phép thành viên tự nhận việc");
            }
            return true;
        }

        // Nếu issue đang do người khác phụ trách:
        if (policy != SelfAssignPolicy.ANY) {
            throw new UnauthorizedActionException("Dự án không cho phép nhận lại công việc đã có người phụ trách");
        }

        return true;
    }

    private boolean isPmOrAdmin(Long projectId, Long userId) {
        if (isAdmin(userId)) {
            return true;
        }
        return projectMemberRepository
                .findByProjectIdAndUserIdAndRoleName(projectId, userId, ProjectRoleEnum.PROJECT_MANAGER.name())
                .isPresent();
    }

    private void assertPmOrAdmin(Long projectId, Long userId) {
        if (!isPmOrAdmin(projectId, userId)) {
            throw new UnauthorizedActionException("Only Project Manager or Admin can perform this action");
        }
    }

    private void assertPmOrReporter(Issue issue, Long userId) {
        boolean isReporter = issue.getReporter().getId().equals(userId);
        boolean isPmOrAdmin = isPmOrAdmin(issue.getProject().getId(), userId);
        if (!isReporter && !isPmOrAdmin) {
            throw new UnauthorizedActionException("Only reporter, PM, or Admin can assign this issue");
        }
    }

    /**
     * Tạo notification log + publish JMS message khi gán hoặc chuyển giao issue.
     * Demo: @Transactional (tạo log), JMS async.
     */
    private void publishAssignedNotification(Issue issue, User recipient, NotificationType type) {
        if (recipient == null || recipient.isMuteTaskEmails()) {
            log.info("Skipping email notification for user {} (muteTaskEmails is enabled or recipient is null)",
                    recipient != null ? recipient.getEmail() : "null");
            return;
        }

        // Tạo notification log với status PENDING
        Notification notification = Notification.builder()
                .user(recipient)
                .issue(issue)
                .type(type)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .build();
        notification = notificationRepository.save(notification);
        Long notifId = (notification != null) ? notification.getId() : null;

        // Publish message vào queue — consumer sẽ gửi email async
        IssueEventMessage message = IssueEventMessage.builder()
                .notificationId(notifId)
                .issueId(issue.getId())
                .projectId(issue.getProject().getId())
                .issueKey(issue.getIssueKey())
                .issueTitle(issue.getTitle())
                .recipientUserId(recipient.getId())
                .recipientEmail(recipient.getEmail())
                .recipientName(recipient.getFullName())
                .type(type)
                .projectKey(issue.getProject().getKey())
                .build();

        notificationProducer.sendNotification(message);
    }

    private void publishAssignedNotification(Issue issue, User recipient) {
        publishAssignedNotification(issue, recipient, NotificationType.ISSUE_ASSIGNED);
    }

    public IssueDTO.Response toIssueResponse(Issue issue) {
        IssueDTO.Response response = issueMapper.toIssueResponse(issue);
        var images = imageStorageService.findAllByEntity(
                com.intern.taskmanagementsystem.enums.ImageEntityType.ISSUE, issue.getId());
        response.setImages(imageStorageService.toImageResponseList(images));
        return response;
    }
}
