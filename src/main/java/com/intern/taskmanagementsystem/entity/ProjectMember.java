package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bảng trung gian project_members — entity riêng vì có cột phụ (project_role_id).
 * Demo kiến thức: N-N với cột phụ (khác với @ManyToMany thuần túy của user_roles).
 */
@Entity
@Table(name = "project_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Role của user trong project này — FK tới project_roles, KHÔNG phải roles hệ thống.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_role_id", nullable = false)
    private ProjectRole projectRole;

}
