package com.intern.taskmanagementsystem.messaging;

import com.intern.taskmanagementsystem.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message object gửi qua JMS queue khi có event liên quan đến issue.
 * Implements Serializable để JMS có thể serialize/deserialize.
 * Demo kiến thức: JMS message object, Serializable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueEventMessage implements Serializable {

    private Long notificationId;    // ID của notification log đã tạo sẵn
    private Long issueId;
    private Long projectId;
    private String issueKey;
    private String issueTitle;
    private Long recipientUserId;
    private String recipientEmail;
    private String recipientName;
    private NotificationType type;
    private String projectKey;
}
