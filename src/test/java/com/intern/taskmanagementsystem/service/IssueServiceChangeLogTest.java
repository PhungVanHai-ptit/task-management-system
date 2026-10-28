package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueChangeLogDTO;
import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.entity.*;
import com.intern.taskmanagementsystem.enums.IssueChangeField;
import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.mapper.IssueChangeLogMapper;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceChangeLogTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private IssueChangeLogRepository issueChangeLogRepository;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private IssueChangeLogMapper issueChangeLogMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private IssueService issueService;

    private Project project;
    private User pm;
    private User userA;
    private User userB;
    private Issue issue;

    @BeforeEach
    void setUp() {
        project = Project.builder().name("Project 1").key("PRJ").build();
        project.setId(10L);

        Role adminRole = Role.builder().id(1L).name("ADMIN").build();
        Role userRole = Role.builder().id(2L).name("USER").build();

        pm = User.builder().email("pm@example.com").role(adminRole).build();
        pm.setId(1L);

        userA = User.builder().email("usera@example.com").role(userRole).fullName("User A").build();
        userA.setId(2L);

        userB = User.builder().email("userb@example.com").role(userRole).fullName("User B").build();
        userB.setId(3L);

        issue = Issue.builder()
                .project(project)
                .reporter(pm)
                .title("Original Title")
                .description("Original Description")
                .priority(IssuePriority.MEDIUM)
                .status(IssueStatus.TODO)
                .dueDate(LocalDateTime.now().plusDays(5))
                .build();
        issue.setId(100L);

        lenient().when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(ProjectMember.builder().project(project).user(pm).build()));
        lenient().when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(ProjectMember.builder().project(project).user(userA).build()));
        lenient().when(projectMemberRepository.findByProjectIdAndUserId(10L, 3L))
                .thenReturn(Optional.of(ProjectMember.builder().project(project).user(userB).build()));

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(pm));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(userA));
        lenient().when(userRepository.findById(3L)).thenReturn(Optional.of(userB));
        lenient().when(userRepository.getReferenceById(1L)).thenReturn(pm);
        lenient().when(userRepository.getReferenceById(2L)).thenReturn(userA);
        lenient().when(userRepository.getReferenceById(3L)).thenReturn(userB);

        lenient().when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        lenient().when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(issueMapper.toIssueResponse(any(Issue.class))).thenReturn(IssueDTO.Response.builder().build());
        lenient().when(imageStorageService.findAllByEntity(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(imageStorageService.toImageResponseList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Partial Update: chỉ cập nhật title, các trường khác null -> không bị NPE và chỉ ghi 1 log TITLE")
    void testPartialUpdateTitleOnly() {
        IssueDTO.UpdateRequest request = new IssueDTO.UpdateRequest();
        request.setTitle("New Updated Title");

        issueService.updateIssue(100L, request, 1L);

        assertEquals("New Updated Title", issue.getTitle());

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository, times(1)).save(captor.capture());
        IssueChangeLog savedLog = captor.getValue();

        assertEquals(IssueChangeField.TITLE, savedLog.getFieldName());
        assertEquals("Original Title", savedLog.getOldValue());
        assertEquals("New Updated Title", savedLog.getNewValue());
        assertNull(savedLog.getTransitionTimeMinutes());
        assertEquals(pm, savedLog.getChangedBy());
    }

    @Test
    @DisplayName("No-op Update: gửi lại đúng title và priority cũ -> không ghi bất kỳ log nào")
    void testNoOpUpdateDoesNotLog() {
        IssueDTO.UpdateRequest request = new IssueDTO.UpdateRequest();
        request.setTitle("Original Title");
        request.setPriority(IssuePriority.MEDIUM);

        issueService.updateIssue(100L, request, 1L);

        verify(issueChangeLogRepository, never()).save(any(IssueChangeLog.class));
    }

    @Test
    @DisplayName("Description update: không lưu nội dung dài vào oldValue/newValue, chỉ lưu null, null")
    void testDescriptionUpdateLogsMarker() {
        IssueDTO.UpdateRequest request = new IssueDTO.UpdateRequest();
        request.setDescription("Very long updated description text...");

        issueService.updateIssue(100L, request, 1L);

        assertEquals("Very long updated description text...", issue.getDescription());

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository, times(1)).save(captor.capture());
        IssueChangeLog savedLog = captor.getValue();

        assertEquals(IssueChangeField.DESCRIPTION, savedLog.getFieldName());
        assertNull(savedLog.getOldValue());
        assertNull(savedLog.getNewValue());
    }

    @Test
    @DisplayName("changeAssignee: gán mới từ null -> userA ghi old_value=null, new_value='2'")
    void testAssignNewUser() {
        boolean changed = issueService.changeAssignee(issue, userA, pm);

        assertTrue(changed);
        assertEquals(userA, issue.getAssignee());

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository).save(captor.capture());
        IssueChangeLog log = captor.getValue();

        assertEquals(IssueChangeField.ASSIGNEE, log.getFieldName());
        assertNull(log.getOldValue());
        assertEquals("2", log.getNewValue());
    }

    @Test
    @DisplayName("changeAssignee: đổi từ userA sang userB ghi old_value='2', new_value='3'")
    void testReassignUser() {
        issue.setAssignee(userA);

        boolean changed = issueService.changeAssignee(issue, userB, pm);

        assertTrue(changed);
        assertEquals(userB, issue.getAssignee());

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository).save(captor.capture());
        IssueChangeLog log = captor.getValue();

        assertEquals(IssueChangeField.ASSIGNEE, log.getFieldName());
        assertEquals("2", log.getOldValue());
        assertEquals("3", log.getNewValue());
    }

    @Test
    @DisplayName("changeAssignee: gỡ gán từ userA sang null ghi old_value='2', new_value=null")
    void testUnassignUser() {
        issue.setAssignee(userA);

        boolean changed = issueService.changeAssignee(issue, null, pm);

        assertTrue(changed);
        assertNull(issue.getAssignee());

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository).save(captor.capture());
        IssueChangeLog log = captor.getValue();

        assertEquals(IssueChangeField.ASSIGNEE, log.getFieldName());
        assertEquals("2", log.getOldValue());
        assertNull(log.getNewValue());
    }

    @Test
    @DisplayName("changeAssignee: gán lại cùng user đã gán -> idempotent, trả false, không ghi log")
    void testAssignSameUserIsNoOp() {
        issue.setAssignee(userA);

        boolean changed = issueService.changeAssignee(issue, userA, pm);

        assertFalse(changed);
        verify(issueChangeLogRepository, never()).save(any(IssueChangeLog.class));
    }

    @Test
    @DisplayName("getChangeLogs: Batch User Enrichment gom toàn bộ ID assignee để gọi findAllById 1 lần duy nhất")
    void testGetChangeLogsBatchEnrichment() {
        IssueChangeLog log1 = IssueChangeLog.builder()
                .id(1L)
                .fieldName(IssueChangeField.ASSIGNEE)
                .oldValue(null)
                .newValue("2")
                .build();

        IssueChangeLog log2 = IssueChangeLog.builder()
                .id(2L)
                .fieldName(IssueChangeField.ASSIGNEE)
                .oldValue("2")
                .newValue("3")
                .build();

        when(issueChangeLogRepository.findByIssueIdOrderByChangedAtAscIdAsc(100L))
                .thenReturn(List.of(log1, log2));

        when(issueChangeLogMapper.toDTO(log1)).thenReturn(IssueChangeLogDTO.builder().id(1L).fieldName(IssueChangeField.ASSIGNEE).oldValue(null).newValue("2").build());
        when(issueChangeLogMapper.toDTO(log2)).thenReturn(IssueChangeLogDTO.builder().id(2L).fieldName(IssueChangeField.ASSIGNEE).oldValue("2").newValue("3").build());

        UserDTO.SummaryResponse summaryA = UserDTO.SummaryResponse.builder().id(2L).fullName("User A").build();
        UserDTO.SummaryResponse summaryB = UserDTO.SummaryResponse.builder().id(3L).fullName("User B").build();

        when(userRepository.findAllById(anySet())).thenReturn(List.of(userA, userB));
        when(userMapper.toUserSummaryResponse(userA)).thenReturn(summaryA);
        when(userMapper.toUserSummaryResponse(userB)).thenReturn(summaryB);

        List<IssueChangeLogDTO> result = issueService.getChangeLogs(100L, 1L);

        assertEquals(2, result.size());

        // Verify userRepository.findAllById only called ONCE for the batch of user IDs
        verify(userRepository, times(1)).findAllById(anySet());

        // Log 1: newAssignee = summaryA
        assertNull(result.get(0).getOldAssignee());
        assertEquals("User A", result.get(0).getNewAssignee().getFullName());

        // Log 2: oldAssignee = summaryA, newAssignee = summaryB
        assertEquals("User A", result.get(1).getOldAssignee().getFullName());
        assertEquals("User B", result.get(1).getNewAssignee().getFullName());
    }
}
