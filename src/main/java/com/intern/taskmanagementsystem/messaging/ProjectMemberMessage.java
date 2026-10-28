package com.intern.taskmanagementsystem.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message object gửi qua JMS queue khi thêm thành viên vào dự án.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberMessage implements Serializable {

    private Long projectId;
    private String projectKey;
    private String projectName;
    private String roleName;           // Tên vai trò trong dự án (DEVELOPER, TESTER, PM ...)
    private Long recipientUserId;
    private String recipientEmail;
    private String recipientName;
}
