package com.intern.taskmanagementsystem.exception;

import com.intern.taskmanagementsystem.enums.IssueStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(IssueStatus from, IssueStatus to) {
        super("Invalid status transition from " + from + " to " + to);
    }
}
