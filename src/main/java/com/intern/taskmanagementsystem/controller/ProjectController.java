package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.ProjectDTO;
import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Project endpoints — /api/projects
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectDTO.Response> createProject(
            @Valid @RequestBody ProjectDTO.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(request, userDetails.getId()));
    }

    @GetMapping
    public ResponseEntity<List<ProjectDTO.Response>> listProjects(
            @RequestParam(value = "filter", required = false, defaultValue = "all") String filter,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(projectService.listProjects(userDetails.getId(), filter));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDTO.Response> getProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(projectService.getProject(projectId, userDetails.getId()));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectDTO.Response> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDTO.UpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request, userDetails.getId()));
    }

    @PatchMapping("/{projectId}/settings")
    public ResponseEntity<ProjectDTO.Response> updateProjectSettings(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDTO.UpdateSettingsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(projectService.updateProjectSettings(projectId, request, userDetails.getId()));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        projectService.deleteProject(projectId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDTO.AddMemberRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        projectService.addMember(projectId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> changeMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody ProjectDTO.ChangeMemberRoleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        projectService.changeMemberRole(projectId, userId, request, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        projectService.removeMember(projectId, userId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members/search-candidates")
    public ResponseEntity<List<UserDTO.Response>> searchCandidateMembers(
            @PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(projectService.searchCandidateMembers(projectId, q, limit, userDetails.getId()));
    }
}
