package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);



    // Lấy user theo role hệ thống (bao gồm cả active và locked)
    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName")
    Page<User> findAllByRoleName(@Param("roleName") String roleName, Pageable pageable);

    // Lấy user theo filter tổng hợp: roleName, keyword (fullName/email), status (ACTIVE/LOCKED/UNVERIFIED)
    @Query("""
            SELECT u FROM User u JOIN u.role r
            WHERE u.deletedAt IS NULL
              AND (:roleName IS NULL OR :roleName = '' OR r.name = :roleName)
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = ''
                   OR (:status = 'ACTIVE' AND u.isLocked = false AND u.emailVerified = true)
                   OR (:status = 'LOCKED' AND u.isLocked = true)
                   OR (:status = 'UNVERIFIED' AND u.isLocked = false AND u.emailVerified = false)
                  )
            """)
    Page<User> findUsersWithFilters(
            @Param("roleName") String roleName,
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    // Tìm kiếm ứng viên để thêm vào dự án
    @Query("""
            SELECT u FROM User u
            JOIN u.role r
            WHERE u.deletedAt IS NULL
              AND u.isLocked = false
              AND u.emailVerified = true
              AND (r.name <> 'ADMIN' OR u.id = :requesterId)
              AND u.id NOT IN (
                  SELECT pm.user.id FROM ProjectMember pm WHERE pm.project.id = :projectId
              )
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY u.fullName ASC
            """)
    List<User> searchCandidateMembers(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("requesterId") Long requesterId,
            Pageable pageable
    );
}
