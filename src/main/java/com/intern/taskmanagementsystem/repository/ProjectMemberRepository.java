package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.ProjectMember;
import com.intern.taskmanagementsystem.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    @EntityGraph(attributePaths = {"user", "projectRole"})
    List<ProjectMember> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"user", "projectRole"})
    List<ProjectMember> findByProjectIdIn(List<Long> projectIds);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);



    @Transactional
    void deleteByProjectIdAndUserId(Long projectId, Long userId);

    // Kiểm tra user có project role cụ thể trong project không
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.user.id = :userId AND UPPER(pm.projectRole.name) = UPPER(:roleName)")
    Optional<ProjectMember> findByProjectIdAndUserIdAndRoleName(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("roleName") String roleName
    );
}
