package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Role hệ thống toàn cục — dùng cho Spring Security (ADMIN, USER).
 * Tách biệt hoàn toàn khỏi ProjectRole.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 50, unique = true, nullable = false)
    private String name;
}
