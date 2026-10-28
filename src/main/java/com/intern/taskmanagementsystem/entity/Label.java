package com.intern.taskmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Label entity — nhãn gắn vào issue (global, toàn hệ thống).
 * Quan hệ N-N với Issue qua issue_labels.
 */
@Entity
@Table(name = "labels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 50, unique = true, nullable = false)
    private String name;

    @ManyToMany(mappedBy = "labels", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Issue> issues = new HashSet<>();
}
