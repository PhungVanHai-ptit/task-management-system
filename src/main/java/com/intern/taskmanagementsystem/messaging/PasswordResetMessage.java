package com.intern.taskmanagementsystem.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message object gửi qua JMS queue khi cần gửi email đặt lại mật khẩu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetMessage implements Serializable {

    private String toEmail;
    private String fullName;
    private String token;
}
