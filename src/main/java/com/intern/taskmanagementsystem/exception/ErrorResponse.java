package com.intern.taskmanagementsystem.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Chuẩn JSON response khi có lỗi.
 * Demo kiến thức: final fields trong DTO, @Builder.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final Map<String, String> fieldErrors;  // chỉ có khi validation fail
    private final Boolean requiresCaptcha;
    private final Integer remainingAttempts;
}
