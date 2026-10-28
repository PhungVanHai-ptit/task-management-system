package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueChangeLogDTO;
import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.IssueChangeLog;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.ProjectMember;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.IssueChangeField;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.IssueChangeLogMapper;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.repository.IssueChangeLogRepository;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.repository.ProjectMemberRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceStatusTransitionTest {

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

    @InjectMocks
    private IssueService issueService;

    private Project project;
    private User reporter;
    private Issue issue;

    @BeforeEach
    void setUp() {
        project = Project.builder().name("Test Project").key("TEST").build();
        project.setId(10L);

        com.intern.taskmanagementsystem.entity.Role userRole =
                com.intern.taskmanagementsystem.entity.Role.builder().id(2L).name("USER").build();

        reporter = User.builder().email("reporter@example.com").role(userRole).build();
        reporter.setId(1L);

        issue = Issue.builder()
                .project(project)
                .reporter(reporter)
                .title("Test Issue")
                .status(IssueStatus.TODO)
                .build();
        issue.setId(100L);
        issue.setCreatedAt(LocalDateTime.now().minusMinutes(60));

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(reporter)
                .build();

        lenient().when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(member));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        lenient().when(userRepository.getReferenceById(1L)).thenReturn(reporter);
        lenient().when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        lenient().when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(issueMapper.toIssueResponse(any(Issue.class))).thenReturn(IssueDTO.Response.builder().build());
        lenient().when(imageStorageService.findAllByEntity(any(), any())).thenReturn(java.util.Collections.emptyList());
        lenient().when(imageStorageService.toImageResponseList(any())).thenReturn(java.util.Collections.emptyList());
    }

    @Test
    @DisplayName("Chuyển từ TODO sang IN_PROGRESS tính đúng timeTodoToProgress và lưu history")
    void testTransitionTodoToInProgress() {
        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.empty());

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.IN_PROGRESS);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.IN_PROGRESS, issue.getStatus());
        assertNotNull(issue.getTimeTodoToProgress());
        assertTrue(issue.getTimeTodoToProgress() >= 59);

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository).save(captor.capture());
        IssueChangeLog savedHistory = captor.getValue();
        assertEquals(IssueChangeField.STATUS, savedHistory.getFieldName());
        assertEquals(IssueStatus.TODO.name(), savedHistory.getOldValue());
        assertEquals(IssueStatus.IN_PROGRESS.name(), savedHistory.getNewValue());
        assertEquals(reporter, savedHistory.getChangedBy());
        assertTrue(savedHistory.getTransitionTimeMinutes() >= 59);
    }

    @Test
    @DisplayName("Chuyển từ IN_PROGRESS sang REVIEW tính đúng timeProgressToReview dựa trên changedAt gần nhất")
    void testTransitionInProgressToReview() {
        issue.setStatus(IssueStatus.IN_PROGRESS);

        IssueChangeLog lastHistory = IssueChangeLog.builder()
                .id(1L)
                .fieldName(IssueChangeField.STATUS)
                .oldValue(IssueStatus.TODO.name())
                .newValue(IssueStatus.IN_PROGRESS.name())
                .changedAt(LocalDateTime.now().minusMinutes(30))
                .build();

        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.of(lastHistory));

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.REVIEW);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.REVIEW, issue.getStatus());
        assertNotNull(issue.getTimeProgressToReview());
        assertTrue(issue.getTimeProgressToReview() >= 29);
        assertNull(issue.getTimeTodoToProgress());
        assertNull(issue.getTimeReviewToDone());

        verify(issueChangeLogRepository).save(any(IssueChangeLog.class));
    }

    @Test
    @DisplayName("Chuyển từ REVIEW sang DONE tính đúng timeReviewToDone (khi là PM)")
    void testTransitionReviewToDone() {
        issue.setStatus(IssueStatus.REVIEW);

        IssueChangeLog lastHistory = IssueChangeLog.builder()
                .id(2L)
                .fieldName(IssueChangeField.STATUS)
                .oldValue(IssueStatus.IN_PROGRESS.name())
                .newValue(IssueStatus.REVIEW.name())
                .changedAt(LocalDateTime.now().minusMinutes(15))
                .build();

        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.of(lastHistory));
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 1L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.DONE);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.DONE, issue.getStatus());
        assertNotNull(issue.getTimeReviewToDone());
        assertTrue(issue.getTimeReviewToDone() >= 14);

        verify(issueChangeLogRepository).save(any(IssueChangeLog.class));
    }

    @Test
    @DisplayName("Nhảy cóc TODO sang DONE phân rã thành 3 bản ghi và chia đều thời gian (khi là PM)")
    void testTransitionDirectTodoToDone() {
        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 1L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.DONE);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.DONE, issue.getStatus());
        assertNotNull(issue.getTimeTodoToProgress());
        assertNotNull(issue.getTimeProgressToReview());
        assertNotNull(issue.getTimeReviewToDone());

        // Thời gian tạo trước 60 phút, chia cho 3 bước = mỗi bước khoảng 20 phút
        assertTrue(issue.getTimeTodoToProgress() >= 19);
        assertTrue(issue.getTimeProgressToReview() >= 19);
        assertTrue(issue.getTimeReviewToDone() >= 19);

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository, times(3)).save(captor.capture());
        List<IssueChangeLog> savedHistories = captor.getAllValues();
        assertEquals(3, savedHistories.size());

        assertEquals(IssueStatus.TODO.name(), savedHistories.get(0).getOldValue());
        assertEquals(IssueStatus.IN_PROGRESS.name(), savedHistories.get(0).getNewValue());

        assertEquals(IssueStatus.IN_PROGRESS.name(), savedHistories.get(1).getOldValue());
        assertEquals(IssueStatus.REVIEW.name(), savedHistories.get(1).getNewValue());

        assertEquals(IssueStatus.REVIEW.name(), savedHistories.get(2).getOldValue());
        assertEquals(IssueStatus.DONE.name(), savedHistories.get(2).getNewValue());
    }

    @Test
    @DisplayName("Nhảy cóc lùi DONE sang TODO phân rã thành 3 bản ghi và chia đều thời gian")
    void testTransitionDirectDoneToTodo() {
        issue.setStatus(IssueStatus.DONE);

        IssueChangeLog lastHistory = IssueChangeLog.builder()
                .id(5L)
                .fieldName(IssueChangeField.STATUS)
                .oldValue(IssueStatus.REVIEW.name())
                .newValue(IssueStatus.DONE.name())
                .changedAt(LocalDateTime.now().minusMinutes(30))
                .build();

        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.of(lastHistory));
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 1L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.TODO);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.TODO, issue.getStatus());
        assertNotNull(issue.getTimeDoneToReview());
        assertNotNull(issue.getTimeReviewToProgress());
        assertNotNull(issue.getTimeProgressToTodo());

        // 30 phút chia đều cho 3 bước = 10 phút/bước
        assertTrue(issue.getTimeDoneToReview() >= 9);
        assertTrue(issue.getTimeReviewToProgress() >= 9);
        assertTrue(issue.getTimeProgressToTodo() >= 9);

        ArgumentCaptor<IssueChangeLog> captor = ArgumentCaptor.forClass(IssueChangeLog.class);
        verify(issueChangeLogRepository, times(3)).save(captor.capture());
        List<IssueChangeLog> savedHistories = captor.getAllValues();

        assertEquals(IssueStatus.DONE.name(), savedHistories.get(0).getOldValue());
        assertEquals(IssueStatus.REVIEW.name(), savedHistories.get(0).getNewValue());

        assertEquals(IssueStatus.REVIEW.name(), savedHistories.get(1).getOldValue());
        assertEquals(IssueStatus.IN_PROGRESS.name(), savedHistories.get(1).getNewValue());

        assertEquals(IssueStatus.IN_PROGRESS.name(), savedHistories.get(2).getOldValue());
        assertEquals(IssueStatus.TODO.name(), savedHistories.get(2).getNewValue());
    }

    @Test
    @DisplayName("Chuyển lùi DONE sang REVIEW cập nhật đúng timeDoneToReview")
    void testTransitionDoneToReview() {
        issue.setStatus(IssueStatus.DONE);

        IssueChangeLog lastHistory = IssueChangeLog.builder()
                .id(6L)
                .fieldName(IssueChangeField.STATUS)
                .oldValue(IssueStatus.REVIEW.name())
                .newValue(IssueStatus.DONE.name())
                .changedAt(LocalDateTime.now().minusMinutes(12))
                .build();

        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.of(lastHistory));
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 1L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.REVIEW);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.REVIEW, issue.getStatus());
        assertNotNull(issue.getTimeDoneToReview());
        assertTrue(issue.getTimeDoneToReview() >= 11);

        verify(issueChangeLogRepository, times(1)).save(any(IssueChangeLog.class));
    }

    @Test
    @DisplayName("Người dùng không phải PM hoặc Admin chuyển sang DONE sẽ bị chặn và ném UnauthorizedActionException")
    void testTransitionToDone_byNonPmOrAdmin_throwsUnauthorizedActionException() {
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(10L, 1L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.empty());

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.DONE);

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                issueService.changeStatus(100L, request, 1L)
        );

        assertTrue(ex.getMessage().contains("Chỉ Project Manager hoặc Admin mới có quyền chuyển trạng thái sang Hoàn thành (DONE)"));
        verify(issueRepository, never()).save(any(Issue.class));
    }

    @Test
    @DisplayName("Admin hệ thống chuyển sang DONE thành công")
    void testTransitionToDone_byAdmin_succeeds() {
        reporter.getRole().setName("ADMIN");
        when(issueChangeLogRepository.findTopByIssueIdAndFieldNameOrderByIdDesc(100L, IssueChangeField.STATUS))
                .thenReturn(Optional.empty());

        IssueDTO.ChangeStatusRequest request = new IssueDTO.ChangeStatusRequest(IssueStatus.DONE);
        issueService.changeStatus(100L, request, 1L);

        assertEquals(IssueStatus.DONE, issue.getStatus());
        verify(issueRepository).save(issue);
    }

    @Test
    @DisplayName("Lấy danh sách lịch sử thay đổi qua getChangeLogs")
    void testGetChangeLogs() {
        IssueChangeLog log = IssueChangeLog.builder()
                .id(1L)
                .fieldName(IssueChangeField.STATUS)
                .oldValue(IssueStatus.TODO.name())
                .newValue(IssueStatus.IN_PROGRESS.name())
                .build();

        List<IssueChangeLog> logs = List.of(log);
        when(issueChangeLogRepository.findByIssueIdOrderByChangedAtAscIdAsc(100L)).thenReturn(logs);
        when(issueChangeLogMapper.toDTO(log)).thenReturn(
                IssueChangeLogDTO.builder().id(1L).fieldName(IssueChangeField.STATUS).oldValue("TODO").newValue("IN_PROGRESS").build()
        );

        List<IssueChangeLogDTO> result = issueService.getChangeLogs(100L, 1L);

        assertEquals(1, result.size());
        assertEquals(IssueChangeField.STATUS, result.get(0).getFieldName());
        assertEquals("TODO", result.get(0).getOldValue());
        assertEquals("IN_PROGRESS", result.get(0).getNewValue());
    }
}
