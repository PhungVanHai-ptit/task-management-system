package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Project entity.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Column(name = "\"key\"", length = 20, unique = true, nullable = false)
    private String key;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", length = 512)
    private String coverImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_assign_policy", length = 30, nullable = false)
    @Builder.Default
    private com.intern.taskmanagementsystem.enums.SelfAssignPolicy selfAssignPolicy = com.intern.taskmanagementsystem.enums.SelfAssignPolicy.UNASSIGNED_ONLY;
}
