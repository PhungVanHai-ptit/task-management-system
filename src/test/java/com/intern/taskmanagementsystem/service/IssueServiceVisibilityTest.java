package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.ProjectMember;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.repository.ProjectMemberRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceVisibilityTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private IssueService issueService;

    private Project project;
    private User reporter;
    private User assignee;
    private Issue publicIssue;
    private Issue privateIssue;

    @BeforeEach
    void setUp() {
        project = Project.builder().name("Test Project").key("TEST").build();
        project.setId(10L);

        reporter = User.builder().email("reporter@example.com").build();
        reporter.setId(1L);

        assignee = User.builder().email("assignee@example.com").build();
        assignee.setId(2L);

        publicIssue = Issue.builder()
                .project(project)
                .reporter(reporter)
                .assignee(assignee)
                .title("Public Task")
                .isPublic(true)
                .build();
        publicIssue.setId(100L);

        privateIssue = Issue.builder()
                .project(project)
                .reporter(reporter)
                .assignee(assignee)
                .title("Private Task")
                .isPublic(false)
                .build();
        privateIssue.setId(101L);
    }

    @Test
    @DisplayName("Khách chưa đăng nhập (currentUserId == null) có thể xem công việc nếu isPublic = true")
    void testGetIssue_WhenPublic_GuestCanView() {
        when(issueRepository.findById(100L)).thenReturn(Optional.of(publicIssue));
        when(issueMapper.toIssueResponse(publicIssue)).thenReturn(IssueDTO.Response.builder().id(100L).isPublic(true).build());
        when(imageStorageService.findAllByEntity(any(), any())).thenReturn(Collections.emptyList());
        when(imageStorageService.toImageResponseList(any())).thenReturn(Collections.emptyList());

        IssueDTO.Response response = issueService.getIssue(100L, null);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertTrue(response.getIsPublic());
    }

    @Test
    @DisplayName("Khách chưa đăng nhập (currentUserId == null) bị chặn khi xem công việc isPublic = false")
    void testGetIssue_WhenPrivate_GuestThrowsUnauthorized() {
        when(issueRepository.findById(101L)).thenReturn(Optional.of(privateIssue));

        assertThrows(UnauthorizedActionException.class, () -> issueService.getIssue(101L, null));
    }

    @Test
    @DisplayName("Người ngoài dự án bị chặn khi xem công việc isPublic = false")
    void testGetIssue_WhenPrivate_NonMemberThrowsUnauthorized() {
        when(issueRepository.findById(101L)).thenReturn(Optional.of(privateIssue));
        when(userRepository.findById(999L)).thenReturn(Optional.empty()); // non-admin
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 999L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedActionException.class, () -> issueService.getIssue(101L, 999L));
    }

    @Test
    @DisplayName("Thành viên dự án xem được công việc isPublic = false")
    void testGetIssue_WhenPrivate_MemberCanView() {
        when(issueRepository.findById(101L)).thenReturn(Optional.of(privateIssue));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.of(new ProjectMember()));
        when(issueMapper.toIssueResponse(privateIssue)).thenReturn(IssueDTO.Response.builder().id(101L).isPublic(false).build());
        when(imageStorageService.findAllByEntity(any(), any())).thenReturn(Collections.emptyList());
        when(imageStorageService.toImageResponseList(any())).thenReturn(Collections.emptyList());

        IssueDTO.Response response = issueService.getIssue(101L, 2L);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertFalse(response.getIsPublic());
    }

    @Test
    @DisplayName("Assignee (không phải PM, không phải Reporter) bị chặn khi cố thay đổi trạng thái chia sẻ")
    void testUpdateIssueVisibility_AssigneeCannotShare() {
        when(issueRepository.findById(101L)).thenReturn(Optional.of(privateIssue));
        // Assignee is member
        when(userRepository.findById(2L)).thenReturn(Optional.empty()); // not system admin
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.of(new ProjectMember()));
        // Not PM
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 2L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.empty());

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.updateIssueVisibility(101L, true, 2L)
        );

        assertTrue(ex.getMessage().contains("Người tạo công việc mới có quyền chia sẻ"));
    }

    @Test
    @DisplayName("Reporter (Người tạo issue) có quyền thay đổi trạng thái chia sẻ sang Public")
    void testUpdateIssueVisibility_ReporterCanShare() {
        when(issueRepository.findById(101L)).thenReturn(Optional.of(privateIssue));
        when(userRepository.findById(1L)).thenReturn(Optional.empty()); // not system admin
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L)).thenReturn(Optional.of(new ProjectMember()));
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 1L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.empty()); // even if not PM, reporter is allowed
        when(issueRepository.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
        when(issueMapper.toIssueResponse(any(Issue.class))).thenReturn(IssueDTO.Response.builder().id(101L).isPublic(true).build());
        when(imageStorageService.findAllByEntity(any(), any())).thenReturn(Collections.emptyList());
        when(imageStorageService.toImageResponseList(any())).thenReturn(Collections.emptyList());

        IssueDTO.Response response = issueService.updateIssueVisibility(101L, true, 1L);

        assertNotNull(response);
        assertTrue(response.getIsPublic());
        verify(issueRepository).save(argThat(Issue::getIsPublic));
    }
}
