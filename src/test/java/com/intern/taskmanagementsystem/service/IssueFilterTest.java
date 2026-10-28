package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.mapper.IssueChangeLogMapper;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import com.intern.taskmanagementsystem.repository.*;
import com.intern.taskmanagementsystem.util.IssueKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueFilterTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LabelRepository labelRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private IssueKeyGenerator issueKeyGenerator;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private CommentService commentService;
    @Mock
    private IssueChangeLogRepository issueChangeLogRepository;
    @Mock
    private IssueChangeLogMapper issueChangeLogMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private IssueService issueService;

    private Project project;
    private User user;
    private Issue overdueIssue;
    private Issue normalIssue;

    @BeforeEach
    void setUp() {
        project = Project.builder().name("Test Proj").key("TEST").build();
        project.setId(1L);

        user = User.builder().fullName("Test User").email("test@example.com").build();
        user.setId(10L);

        overdueIssue = Issue.builder()
                .title("Overdue task")
                .status(IssueStatus.IN_PROGRESS)
                .priority(IssuePriority.HIGH)
                .dueDate(LocalDateTime.now().minusDays(2))
                .assignee(user)
                .project(project)
                .build();
        overdueIssue.setId(101L);

        normalIssue = Issue.builder()
                .title("Normal task")
                .status(IssueStatus.TODO)
                .priority(IssuePriority.MEDIUM)
                .dueDate(LocalDateTime.now().plusDays(5))
                .assignee(user)
                .project(project)
                .build();
        normalIssue.setId(102L);
    }

    @Test
    @DisplayName("Filter issues with isOverdue=true delegates to repository with now timestamp")
    void testListIssues_withOverdueFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Issue> issuePage = new PageImpl<>(List.of(overdueIssue), pageable, 1);

        when(issueRepository.findByProjectWithFilters(
                eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(Boolean.TRUE), any(LocalDateTime.class), eq(pageable)
        )).thenReturn(issuePage);

        IssueDTO.Response mockResponse = new IssueDTO.Response();
        mockResponse.setId(101L);
        mockResponse.setTitle("Overdue task");
        when(issueMapper.toIssueResponse(overdueIssue)).thenReturn(mockResponse);

        Page<IssueDTO.Response> result = issueService.listIssues(1L, null, null, null, null, null, true, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(101L, result.getContent().get(0).getId());
        verify(issueRepository).findByProjectWithFilters(
                eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(Boolean.TRUE), any(LocalDateTime.class), eq(pageable)
        );
    }

    @Test
    @DisplayName("Filter issues with assigneeId (myOnly) filters issues of current user")
    void testListIssues_withAssigneeFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Issue> issuePage = new PageImpl<>(List.of(normalIssue), pageable, 1);

        when(issueRepository.findByProjectWithFilters(
                eq(1L), isNull(), isNull(), eq(10L), isNull(), isNull(), isNull(), any(LocalDateTime.class), eq(pageable)
        )).thenReturn(issuePage);

        IssueDTO.Response mockResponse = new IssueDTO.Response();
        mockResponse.setId(102L);
        mockResponse.setTitle("Normal task");
        when(issueMapper.toIssueResponse(normalIssue)).thenReturn(mockResponse);

        Page<IssueDTO.Response> result = issueService.listIssues(1L, null, null, 10L, null, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(102L, result.getContent().get(0).getId());
    }
}
