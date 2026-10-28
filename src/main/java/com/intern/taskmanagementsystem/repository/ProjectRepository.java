package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {



    Optional<Project> findByKey(String key);

    // Lấy danh sách project mà user đang tham gia (truy vấn qua ProjectMember, không phụ thuộc field members của Project)
    @Query("SELECT DISTINCT pm.project FROM ProjectMember pm WHERE pm.user.id = :userId")
    List<Project> findAllByMemberUserId(@Param("userId") Long userId);

    // Lấy danh sách project do user tạo ra (hoặc là PM với các project cũ chưa có creator_id)
    @Query("SELECT DISTINCT p FROM Project p WHERE p.creator.id = :userId OR (p.creator IS NULL AND EXISTS (SELECT pm FROM ProjectMember pm WHERE pm.project = p AND pm.user.id = :userId AND UPPER(pm.projectRole.name) = 'PROJECT_MANAGER'))")
    List<Project> findAllCreatedByUserId(@Param("userId") Long userId);

    // Lấy danh sách project có ít nhất 1 issue được giao cho user
    @Query("SELECT DISTINCT i.project FROM Issue i WHERE i.assignee.id = :userId")
    List<Project> findAllWithIssuesAssignedToUser(@Param("userId") Long userId);
}
