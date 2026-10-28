package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.ProjectDTO;
import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.entity.*;
import com.intern.taskmanagementsystem.enums.ImageEntityType;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.*;
import com.intern.taskmanagementsystem.mapper.ProjectMapper;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.messaging.ProjectMemberMessage;
import com.intern.taskmanagementsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ProjectService: quản lý project, thành viên.
 * Demo kiến thức: @Transactional, @PreAuthorize, N-N với cột phụ (ProjectMember), MapStruct.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final UserRepository userRepository;
    private final IssueService issueService;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ImageStorageService imageStorageService;
    private final NotificationProducer notificationProducer;

    /**
     * Tạo project mới — người tạo tự động thành PROJECT_MANAGER.
     */
    @Transactional
    public ProjectDTO.Response createProject(ProjectDTO.CreateRequest request, Long creatorId) {
        if (projectRepository.findByKey(request.getKey()).isPresent()) {
            throw new DuplicateResourceException("Mã dự án đã tồn tại: " + request.getKey());
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", creatorId));

        Project project = projectMapper.toEntity(request);
        project.setCreator(creator);
        project = projectRepository.save(project);

        // Thêm người tạo vào project với role PROJECT_MANAGER
        ProjectRole pmRole = projectRoleRepository.findByName(ProjectRoleEnum.PROJECT_MANAGER.name())
                .orElseThrow(() -> new ResourceNotFoundException("ProjectRole PROJECT_MANAGER not found"));

        ProjectMember member = ProjectMember.builder()
                .id(new ProjectMemberId(project.getId(), creatorId))
                .project(project)
                .user(creator)
                .projectRole(pmRole)
                .build();
        projectMemberRepository.save(member);

        log.info("Created project [{}] by user id={}", request.getKey(), creatorId);
        return toProjectResponse(project);
    }

    /**
     * Lấy danh sách project theo filter:
     * - filter = "my" (Dự Án Của Tôi): Lấy các dự án do bản thân tạo ra.
     * - filter = "all" hoặc null (Tất Cả Dự Án):
     *     + Với Admin: Lấy tất cả dự án trong hệ thống.
     *     + Với User thường: Lấy các dự án mà user đó là thành viên.
     */
    @Transactional(readOnly = true)
    public List<ProjectDTO.Response> listProjects(Long userId, String filter) {
        if ("my".equalsIgnoreCase(filter)) {
            return toProjectResponseList(projectRepository.findAllCreatedByUserId(userId));
        }

        if (isAdmin(userId)) {
            return toProjectResponseList(projectRepository.findAll());
        }
        return toProjectResponseList(projectRepository.findAllByMemberUserId(userId));
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO.Response> listMyProjects(Long userId) {
        return listProjects(userId, "all");
    }

    /**
     * Xem chi tiết project (kèm danh sách thành viên).
     */
    @Transactional(readOnly = true)
    public ProjectDTO.Response getProject(Long projectId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        assertMember(projectId, userId);
        return toProjectResponse(project);
    }

    /**
     * Cập nhật project — chỉ PM hoặc ADMIN.
     */
    @Transactional
    public ProjectDTO.Response updateProject(Long projectId, ProjectDTO.UpdateRequest request, Long userId) {
        Project project = findProjectOrThrow(projectId);
        assertPmOrAdmin(projectId, userId);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription().trim());
        }
        projectRepository.save(project);
        log.info("Updated project id={} by user id={}", projectId, userId);
        return toProjectResponse(project);
    }

    /**
     * Cập nhật cài đặt của project (ví dụ: chính sách tự nhận việc) — chỉ PM hoặc Admin của đúng project đó.
     */
    @Transactional
    public ProjectDTO.Response updateProjectSettings(Long projectId, ProjectDTO.UpdateSettingsRequest request, Long userId) {
        Project project = findProjectOrThrow(projectId);
        assertPmOrAdmin(projectId, userId);

        if (request.getSelfAssignPolicy() != null) {
            project.setSelfAssignPolicy(request.getSelfAssignPolicy());
        }
        project = projectRepository.save(project);
        log.info("Updated project settings id={} selfAssignPolicy={} by user id={}", projectId, project.getSelfAssignPolicy(), userId);
        return toProjectResponse(project);
    }

    /**
     * Xoá project — chỉ PM hoặc ADMIN (cascade xoá issue, comment).
     */
    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        assertPmOrAdmin(projectId, userId);

        // 1. Dọn dẹp toàn bộ issues, comments và ảnh liên quan trong project (giao cho IssueService)
        issueService.deleteAllByProjectId(projectId);

        // 2. Xóa tất cả thành viên trong project (tránh xung đột TransientObjectException trong Hibernate Session)
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        projectMemberRepository.deleteAll(members);

        // 3. Xóa file ảnh bìa project trên disk nếu có
        if (project.getCoverImageUrl() != null) {
            imageStorageService.deleteDiskFile(project.getCoverImageUrl());
        }

        projectRepository.delete(project);
        log.info("Deleted project id={} by user id={}", projectId, userId);
    }

    /**
     * Thêm thành viên vào project.
     */
    @Transactional
    public void addMember(Long projectId, ProjectDTO.AddMemberRequest request, Long requesterId) {
        assertPmOrAdmin(projectId, requesterId);
        findProjectOrThrow(projectId);

        if (projectMemberRepository.findByProjectIdAndUserId(projectId, request.getUserId()).isPresent()) {
            throw new DuplicateResourceException("Người dùng đã là thành viên của dự án này");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Không thể thêm tài khoản chưa xác thực email vào dự án");
        }

        if (user.isLocked() || user.getDeletedAt() != null) {
            throw new IllegalArgumentException("Không thể thêm tài khoản đã bị khóa vào dự án");
        }

        if (user.getRole() != null && "ADMIN".equals(user.getRole().getName()) && !user.getId().equals(requesterId)) {
            throw new IllegalArgumentException("Không thể thêm tài khoản Quản trị viên khác vào dự án");
        }

        ProjectRole projectRole = projectRoleRepository.findById(request.getProjectRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("ProjectRole", request.getProjectRoleId()));
        Project project = findProjectOrThrow(projectId);

        ProjectMember member = ProjectMember.builder()
                .id(new ProjectMemberId(projectId, request.getUserId()))
                .project(project)
                .user(user)
                .projectRole(projectRole)
                .build();
        projectMemberRepository.save(member);

        // Gửi thông báo qua email khi được thêm vào dự án
        ProjectMemberMessage message = ProjectMemberMessage.builder()
                .projectId(project.getId())
                .projectKey(project.getKey())
                .projectName(project.getName())
                .roleName(projectRole.getName())
                .recipientUserId(user.getId())
                .recipientEmail(user.getEmail())
                .recipientName(user.getFullName())
                .build();
        notificationProducer.sendProjectMemberAdded(message);
    }

    /**
     * Đổi project role của 1 thành viên.
     * - PM không được tự đổi role của chính mình trong dự án.
     * - Admin có quyền đổi role của bất kỳ ai trong dự án.
     */
    @Transactional
    public void changeMemberRole(Long projectId, Long userId, ProjectDTO.ChangeMemberRoleRequest request, Long requesterId) {
        assertPmOrAdmin(projectId, requesterId);

        boolean requesterIsAdmin = isAdmin(requesterId);
        if (!requesterIsAdmin && requesterId.equals(userId)) {
            throw new UnauthorizedActionException("Project Manager không thể tự đổi role của chính mình trong dự án");
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in project"));
        ProjectRole newRole = projectRoleRepository.findById(request.getProjectRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("ProjectRole", request.getProjectRoleId()));

        member.setProjectRole(newRole);
        projectMemberRepository.save(member);
        log.info("Changed role of user id={} in project id={} to role [{}] by requester id={}",
                userId, projectId, newRole.getName(), requesterId);
    }

    /**
     * Xoá thành viên khỏi project.
     * - PM không được tự xóa chính mình khỏi dự án.
     * - Admin có quyền xóa bất kỳ ai khỏi dự án.
     */
    @Transactional
    public void removeMember(Long projectId, Long userId, Long requesterId) {
        assertPmOrAdmin(projectId, requesterId);

        boolean requesterIsAdmin = isAdmin(requesterId);
        if (!requesterIsAdmin && requesterId.equals(userId)) {
            throw new UnauthorizedActionException("Project Manager không thể tự xóa chính mình khỏi dự án");
        }

        if (projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Thành viên không tồn tại trong dự án");
        }
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
        log.info("Removed user id={} from project id={} by requester id={}", userId, projectId, requesterId);
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private Project findProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private void assertMember(Long projectId, Long userId) {
        if (isAdmin(userId)) {
            return;
        }
        if (projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
            throw new UnauthorizedActionException("Bạn không phải là thành viên của dự án này");
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole().getName().equals(SystemRole.ADMIN.name()))
                .orElse(false);
    }

    public void assertPmOrAdmin(Long projectId, Long userId) {
        // Admin check via system role
        if (isAdmin(userId)) {
            return;
        }

        // PM check via project role
        boolean isPm = projectMemberRepository
                .findByProjectIdAndUserIdAndRoleName(projectId, userId, ProjectRoleEnum.PROJECT_MANAGER.name())
                .isPresent();

        if (!isPm) {
            throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền thực hiện thao tác này");
        }
    }

    public ProjectDTO.Response toProjectResponse(Project project) {
        ProjectDTO.Response response = projectMapper.toProjectResponse(project);
        List<ProjectMember> members = projectMemberRepository.findByProjectId(project.getId());
        List<ProjectDTO.MemberResponse> memberResponses = members.stream()
                .map(projectMapper::toProjectMemberResponse)
                .toList();
        response.setMembers(memberResponses);

        if (response.getCreator() == null && !members.isEmpty()) {
            members.stream()
                    .filter(m -> m.getProjectRole() != null && "PROJECT_MANAGER".equalsIgnoreCase(m.getProjectRole().getName()))
                    .findFirst()
                    .ifPresent(m -> response.setCreator(userMapper.toUserResponse(m.getUser())));
        }
        return response;
    }

    public List<ProjectDTO.Response> toProjectResponseList(List<Project> projects) {
        if (projects == null || projects.isEmpty()) return List.of();

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<ProjectMember> allMembers = projectMemberRepository.findByProjectIdIn(projectIds);
        Map<Long, List<ProjectMember>> membersByProjectId = allMembers.stream()
                .collect(Collectors.groupingBy(m -> m.getProject().getId()));

        return projects.stream().map(project -> {
            ProjectDTO.Response response = projectMapper.toProjectResponse(project);
            List<ProjectMember> members = membersByProjectId.getOrDefault(project.getId(), List.of());
            List<ProjectDTO.MemberResponse> memberResponses = members.stream()
                    .map(projectMapper::toProjectMemberResponse)
                    .toList();
            response.setMembers(memberResponses);

            if (response.getCreator() == null && !members.isEmpty()) {
                members.stream()
                        .filter(m -> m.getProjectRole() != null && "PROJECT_MANAGER".equalsIgnoreCase(m.getProjectRole().getName()))
                        .findFirst()
                        .ifPresent(m -> response.setCreator(userMapper.toUserResponse(m.getUser())));
            }
            return response;
        }).toList();
    }

    /**
     * Tìm kiếm ứng viên để thêm vào dự án:
     * - Chỉ PM hoặc ADMIN có quyền tìm kiếm
     * - Loại trừ tài khoản đã là thành viên trong dự án
     * - Loại trừ tài khoản bị khóa, xóa mềm hoặc chưa xác thực email
     * - Loại trừ tài khoản ADMIN khác (chỉ hiển thị ADMIN nếu chính ADMIN đó đang tìm kiếm để tự thêm mình)
     */
    @Transactional(readOnly = true)
    public List<UserDTO.Response> searchCandidateMembers(Long projectId, String query, Integer limit, Long requesterId) {
        assertPmOrAdmin(projectId, requesterId);
        findProjectOrThrow(projectId);

        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 20);
        String keyword = query != null ? query.trim() : "";

        List<User> candidates = userRepository.searchCandidateMembers(
                projectId,
                keyword,
                requesterId,
                PageRequest.of(0, safeLimit)
        );

        return candidates.stream()
                .map(userMapper::toUserResponse)
                .toList();
    }
}
