package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.dto.LabelDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Label;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.IssueMapper;
import com.intern.taskmanagementsystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceLabelTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LabelRepository labelRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private IssueMapper issueMapper;
    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private IssueService issueService;

    private Project project;
    private Issue issue;
    private Label label;

    @BeforeEach
    void setUp() {
        project = Project.builder().name("Test Proj").key("TEST").build();
        project.setId(100L);

        label = Label.builder().id(1L).name("bug").build();

        issue = Issue.builder()
                .project(project)
                .title("Issue 1")
                .labels(new HashSet<>())
                .build();
        issue.setId(10L);
    }

    @Test
    @DisplayName("addLabel: gắn nhãn vào issue thành công khi là thành viên")
    void testAddLabel_Success() {
        when(issueRepository.findById(10L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(5L)).thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(100L, 5L)).thenReturn(Optional.of(mock(com.intern.taskmanagementsystem.entity.ProjectMember.class)));
        when(labelRepository.findById(1L)).thenReturn(Optional.of(label));
        when(issueRepository.save(any(Issue.class))).thenReturn(issue);
        when(issueMapper.toIssueResponse(any(Issue.class))).thenReturn(
                IssueDTO.Response.builder().id(10L).labels(Set.of(new LabelDTO.Response(1L, "bug"))).build()
        );

        IssueDTO.Response response = issueService.addLabel(10L, new IssueDTO.AddLabelRequest(1L), 5L);

        assertNotNull(response);
        assertTrue(issue.getLabels().contains(label));
        verify(issueRepository).save(issue);
    }

    @Test
    @DisplayName("addLabel: ném UnauthorizedActionException khi không phải thành viên dự án")
    void testAddLabel_NotMember() {
        when(issueRepository.findById(10L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(100L, 99L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedActionException.class, () ->
                issueService.addLabel(10L, new IssueDTO.AddLabelRequest(1L), 99L)
        );
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeLabel: gỡ nhãn khỏi issue thành công")
    void testRemoveLabel_Success() {
        issue.getLabels().add(label);
        when(issueRepository.findById(10L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(5L)).thenReturn(Optional.empty());
        when(projectMemberRepository.findByProjectIdAndUserId(100L, 5L)).thenReturn(Optional.of(mock(com.intern.taskmanagementsystem.entity.ProjectMember.class)));
        when(issueRepository.save(any(Issue.class))).thenReturn(issue);
        when(issueMapper.toIssueResponse(any(Issue.class))).thenReturn(
                IssueDTO.Response.builder().id(10L).labels(Set.of()).build()
        );

        IssueDTO.Response response = issueService.removeLabel(10L, 1L, 5L);

        assertNotNull(response);
        assertFalse(issue.getLabels().contains(label));
        verify(issueRepository).save(issue);
    }
}
