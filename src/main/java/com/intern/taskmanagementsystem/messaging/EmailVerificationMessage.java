package com.intern.taskmanagementsystem.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message object gửi qua JMS queue khi cần gửi email xác thực tài khoản.
 * Implements Serializable để JMS có thể serialize/deserialize.
 * Demo kiến thức: JMS message object, Serializable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationMessage implements Serializable {

    private String toEmail;
    private String fullName;
    private String token;
}
