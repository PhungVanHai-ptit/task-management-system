package com.intern.taskmanagementsystem.enums;

/**
 * Chính sách tự gán công việc (Self-assign) của dự án:
 * - NONE: Thành viên không được tự nhận việc (chỉ PM/Admin được phân công).
 * - UNASSIGNED_ONLY: Thành viên chỉ được tự nhận các công việc chưa có ai phụ trách.
 * - ANY: Thành viên được tự nhận việc chưa gán và cả việc đang do người khác phụ trách.
 */
public enum SelfAssignPolicy {
    NONE,
    UNASSIGNED_ONLY,
    ANY
}
