package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Role trong ngữ cảnh project — tách riêng khỏi Role hệ thống.
 * Ví dụ: PROJECT_MANAGER, DEVELOPER, REPORTER, TESTER, BA
 */
@Entity
@Table(name = "project_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 50, unique = true, nullable = false)
    private String name;
}
