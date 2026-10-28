package com.intern.taskmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO chứa kết quả import danh sách Issue từ file Excel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {

    private int totalRows;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    public record RowError(int row, String field, String message) {}
}
