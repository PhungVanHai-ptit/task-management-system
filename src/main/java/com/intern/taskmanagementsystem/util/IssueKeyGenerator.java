package com.intern.taskmanagementsystem.util;

import com.intern.taskmanagementsystem.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Sinh issue key theo pattern PROJECT_KEY-N (ví dụ PROJ-1, PROJ-2...).
 *
 * Demo kiến thức:
 *  - synchronized: tránh race condition khi nhiều request tạo issue cùng lúc
 *  - static utility pattern (method tách rời, không phụ thuộc state)
 */
@Component
@RequiredArgsConstructor
public class IssueKeyGenerator {

    private final IssueRepository issueRepository;

    /**
     * Sinh issueKey tiếp theo cho một project.
     * Phương thức được đồng bộ hoá để tránh race condition.
     *
     * Demo: synchronized keyword (Java core - synchronize-vs-asynchronize)
     */
    public synchronized String generateNextKey(String projectKey) {
        String prefix = projectKey + "-";
        java.util.List<String> keys = issueRepository.findIssueKeysByPrefix(prefix);

        int maxNumber = 0;
        for (String key : keys) {
            if (key != null && key.startsWith(prefix)) {
                try {
                    String numberPart = key.substring(prefix.length());
                    int num = Integer.parseInt(numberPart);
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return prefix + (maxNumber + 1);
    }

    /**
     * Utility static: parse project key từ issue key.
     * Demo: static method.
     */
    public static String extractProjectKey(String issueKey) {
        if (issueKey == null || !issueKey.contains("-")) {
            throw new IllegalArgumentException("Invalid issue key format: " + issueKey);
        }
        return issueKey.substring(0, issueKey.lastIndexOf("-"));
    }
}
