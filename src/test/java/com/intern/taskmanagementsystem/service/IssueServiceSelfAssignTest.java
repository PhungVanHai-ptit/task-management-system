package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.ProjectMember;
import com.intern.taskmanagementsystem.entity.Role;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.NotificationType;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.enums.SelfAssignPolicy;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.*;
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
class IssueServiceSelfAssignTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private com.intern.taskmanagementsystem.repository.IssueChangeLogRepository issueChangeLogRepository;
    @Mock
    private com.intern.taskmanagementsystem.mapper.IssueChangeLogMapper issueChangeLogMapper;
    @Mock
    private com.intern.taskmanagementsystem.mapper.UserMapper userMapper;

    @InjectMocks
    private IssueService issueService;

    private Project project;
    private User memberUser;
    private User oldAssignee;
    private User adminUser;
    private Issue issue;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .key("TEST")
                .name("Test Project")
                .selfAssignPolicy(SelfAssignPolicy.UNASSIGNED_ONLY)
                .build();
        project.setId(10L);

        Role userRole = Role.builder().id(2L).name(SystemRole.USER.name()).build();
        Role adminRole = Role.builder().id(1L).name(SystemRole.ADMIN.name()).build();

        memberUser = User.builder()
                .email("member@example.com")
                .fullName("Member User")
                .role(userRole)
                .emailVerified(true)
                .isLocked(false)
                .build();
        memberUser.setId(1L);

        oldAssignee = User.builder()
                .email("old@example.com")
                .fullName("Old Assignee")
                .role(userRole)
                .emailVerified(true)
                .isLocked(false)
                .build();
        oldAssignee.setId(2L);

        adminUser = User.builder()
                .email("admin@example.com")
                .fullName("Admin User")
                .role(adminRole)
                .emailVerified(true)
                .isLocked(false)
                .build();
        adminUser.setId(99L);

        issue = Issue.builder()
                .project(project)
                .reporter(memberUser)
                .title("Test Task")
                .status(IssueStatus.TODO)
                .build();
        issue.setId(100L);

        lenient().when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        lenient().when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(issueMapper.toIssueResponse(any(Issue.class))).thenReturn(IssueDTO.Response.builder().build());
        lenient().when(imageStorageService.findAllByEntity(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(imageStorageService.toImageResponseList(any())).thenReturn(Collections.emptyList());
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> {
            com.intern.taskmanagementsystem.entity.Notification notif = inv.getArgument(0);
            notif.setId(555L);
            return notif;
        });
    }

    @Test
    @DisplayName("1. Lỗi IDOR: URL projectId không khớp với project của issue -> ném IllegalArgumentException")
    void testAssignToMe_mismatchedProjectId_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                issueService.assignToMe(999L, 100L, 1L)
        );
        assertTrue(ex.getMessage().contains("Công việc không thuộc về dự án này"));
    }

    @Test
    @DisplayName("2. Không cho nhận issue đã DONE -> ném IllegalArgumentException")
    void testAssignToMe_issueDone_throwsException() {
        issue.setStatus(IssueStatus.DONE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                issueService.assignToMe(10L, 100L, 1L)
        );
        assertTrue(ex.getMessage().contains("Không thể thay đổi người phụ trách cho công việc đã hoàn thành"));
    }

    @Test
    @DisplayName("3. Tài khoản bị khóa hoặc chưa xác thực email -> ném IllegalArgumentException")
    void testAssignToMe_unverifiedOrLocked_throwsException() {
        memberUser.setEmailVerified(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                issueService.assignToMe(10L, 100L, 1L)
        );
        assertTrue(ex.getMessage().contains("chưa xác thực email"));

        memberUser.setEmailVerified(true);
        memberUser.setLocked(true);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                issueService.assignToMe(10L, 100L, 1L)
        );
        assertTrue(ex2.getMessage().contains("đã bị khóa"));
    }

    @Test
    @DisplayName("4. Admin hoặc user chưa tham gia dự án -> ném UnauthorizedActionException (bắt buộc phải là thành viên)")
    void testAssignToMe_userNotProjectMember_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.assignToMe(10L, 100L, 99L)
        );
        assertTrue(ex.getMessage().contains("phải là thành viên của dự án"));
    }

    @Test
    @DisplayName("5. Policy là NONE: Thành viên thường bấm nhận việc chưa gán -> ném UnauthorizedActionException")
    void testAssignToMe_policyNone_throwsException() {
        project.setSelfAssignPolicy(SelfAssignPolicy.NONE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.assignToMe(10L, 100L, 1L)
        );
        assertTrue(ex.getMessage().contains("Dự án hiện không cho phép thành viên tự nhận việc"));
    }

    @Test
    @DisplayName("6. Policy là UNASSIGNED_ONLY: Nhận việc chưa gán thành công, KHÔNG gửi mail cho chính mình")
    void testAssignToMe_policyUnassignedOnly_success() {
        project.setSelfAssignPolicy(SelfAssignPolicy.UNASSIGNED_ONLY);
        issue.setAssignee(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        issueService.assignToMe(10L, 100L, 1L);

        assertEquals(memberUser, issue.getAssignee());
        // verify KHÔNG gửi mail cho chính người bấm
        verify(notificationProducer, never()).sendNotification(any());
    }

    @Test
    @DisplayName("7. Policy là UNASSIGNED_ONLY: Thành viên cố nhận việc đã có người -> ném UnauthorizedActionException")
    void testAssignToMe_policyUnassignedOnly_alreadyAssigned_throwsException() {
        project.setSelfAssignPolicy(SelfAssignPolicy.UNASSIGNED_ONLY);
        issue.setAssignee(oldAssignee);

        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.assignToMe(10L, 100L, 1L)
        );
        assertTrue(ex.getMessage().contains("Dự án không cho phép nhận lại công việc đã có người phụ trách"));
    }

    @Test
    @DisplayName("8. Policy là ANY: Thành viên nhận việc từ người khác thành công -> gửi mail thông báo cho assignee cũ")
    void testAssignToMe_policyAny_reassign_success_andNotifiesOldAssignee() {
        project.setSelfAssignPolicy(SelfAssignPolicy.ANY);
        issue.setAssignee(oldAssignee);

        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        issueService.assignToMe(10L, 100L, 1L);

        assertEquals(memberUser, issue.getAssignee());
        // verify có gửi notification ISSUE_REASSIGNED cho oldAssignee
        verify(notificationProducer, times(1)).sendNotification(argThat(msg ->
                msg.getType() == NotificationType.ISSUE_REASSIGNED &&
                msg.getRecipientUserId().equals(2L)
        ));
    }

    @Test
    @DisplayName("9. Idempotency: Khi đã là assignee rồi bấm lại assign-to-me -> trả 200, không cập nhật DB hay gửi mail")
    void testAssignToMe_alreadyAssignee_idempotent() {
        issue.setAssignee(memberUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IssueDTO.Response res = issueService.assignToMe(10L, 100L, 1L);
        assertNotNull(res);

        verify(issueRepository, never()).save(any());
        verify(notificationProducer, never()).sendNotification(any());
    }

    @Test
    @DisplayName("10. Hủy nhận việc (unassign-me) thành công khi chính mình đang phụ trách")
    void testUnassignMe_byCurrentAssignee_success() {
        project.setSelfAssignPolicy(SelfAssignPolicy.UNASSIGNED_ONLY);
        issue.setAssignee(memberUser);

        issueService.unassignMe(10L, 100L, 1L);

        assertNull(issue.getAssignee());
        verify(issueRepository).save(issue);
    }

    @Test
    @DisplayName("11. Hủy nhận việc (unassign-me) bị từ chối nếu không phải người phụ trách hiện tại")
    void testUnassignMe_byNonAssignee_throwsException() {
        project.setSelfAssignPolicy(SelfAssignPolicy.UNASSIGNED_ONLY);
        issue.setAssignee(oldAssignee);

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.unassignMe(10L, 100L, 1L)
        );
        assertTrue(ex.getMessage().contains("Chỉ người phụ trách hiện tại hoặc Project Manager"));
    }

    @Test
    @DisplayName("12. Thành viên thường gọi assignIssue để gán cho người khác -> ném UnauthorizedActionException")
    void testAssignIssue_memberAssignsToOther_throwsException() {
        project.setSelfAssignPolicy(SelfAssignPolicy.ANY);
        when(userRepository.findById(2L)).thenReturn(Optional.of(oldAssignee));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IssueDTO.AssignRequest req = new IssueDTO.AssignRequest(2L);
        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.assignIssue(10L, 100L, req, 1L)
        );
        assertTrue(ex.getMessage().contains("Thành viên chỉ được tự gán việc cho chính mình"));
    }
}
