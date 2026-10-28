package com.intern.taskmanagementsystem.exception;

import lombok.Getter;

@Getter
public class LoginFailedException extends RuntimeException {
    private final boolean requiresCaptcha;
    private final int remainingAttempts;

    public LoginFailedException(String message, boolean requiresCaptcha, int remainingAttempts) {
        super(message);
        this.requiresCaptcha = requiresCaptcha;
        this.remainingAttempts = remainingAttempts;
    }
}
