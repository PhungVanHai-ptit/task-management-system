package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.ProjectDTO;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.ProjectMember;
import com.intern.taskmanagementsystem.entity.Role;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.enums.ProjectRoleEnum;
import com.intern.taskmanagementsystem.enums.SelfAssignPolicy;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.ProjectMapper;
import com.intern.taskmanagementsystem.repository.ProjectMemberRepository;
import com.intern.taskmanagementsystem.repository.ProjectRepository;
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
class ProjectServiceSettingsTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ProjectService projectService;

    private Project project;
    private User pmUser;
    private User memberUser;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .key("ALPHA")
                .name("Alpha Project")
                .selfAssignPolicy(SelfAssignPolicy.NONE)
                .build();
        project.setId(1L);

        Role userRole = Role.builder().id(2L).name(SystemRole.USER.name()).build();

        pmUser = User.builder().email("pm@example.com").role(userRole).build();
        pmUser.setId(10L);

        memberUser = User.builder().email("member@example.com").role(userRole).build();
        memberUser.setId(20L);

        lenient().when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        lenient().when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(projectMemberRepository.findByProjectId(1L)).thenReturn(Collections.emptyList());
        lenient().when(projectMapper.toProjectResponse(any(Project.class))).thenReturn(ProjectDTO.Response.builder().build());
    }

    @Test
    @DisplayName("Thành viên thường không phải PM/Admin đổi settings -> ném UnauthorizedActionException")
    void testUpdateProjectSettings_byMember_throwsUnauthorized() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(memberUser));
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(1L, 20L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.empty());

        ProjectDTO.UpdateSettingsRequest req = new ProjectDTO.UpdateSettingsRequest(SelfAssignPolicy.ANY);

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () ->
                projectService.updateProjectSettings(1L, req, 20L)
        );
        assertTrue(ex.getMessage().contains("Chỉ Project Manager hoặc Admin"));
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("PM của dự án đổi settings thành công")
    void testUpdateProjectSettings_byPm_success() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(pmUser));
        when(projectMemberRepository.findByProjectIdAndUserIdAndRoleName(1L, 10L, ProjectRoleEnum.PROJECT_MANAGER.name()))
                .thenReturn(Optional.of(ProjectMember.builder().build()));

        ProjectDTO.UpdateSettingsRequest req = new ProjectDTO.UpdateSettingsRequest(SelfAssignPolicy.ANY);

        projectService.updateProjectSettings(1L, req, 10L);

        assertEquals(SelfAssignPolicy.ANY, project.getSelfAssignPolicy());
        verify(projectRepository).save(project);
    }
}
