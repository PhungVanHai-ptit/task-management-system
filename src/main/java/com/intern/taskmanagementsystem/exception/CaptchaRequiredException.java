package com.intern.taskmanagementsystem.exception;

public class CaptchaRequiredException extends RuntimeException {
    public CaptchaRequiredException(String message) {
        super(message);
    }
}
