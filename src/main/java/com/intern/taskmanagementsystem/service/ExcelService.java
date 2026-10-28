package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.ImportResultDTO;
import com.intern.taskmanagementsystem.entity.*;
import com.intern.taskmanagementsystem.enums.ImageEntityType;
import com.intern.taskmanagementsystem.enums.IssuePriority;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.repository.*;
import com.intern.taskmanagementsystem.util.IssueKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý xuất / nhập Excel và tạo file mẫu cho Issue theo chuẩn Jira.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final CommentRepository commentRepository;
    private final ImageRepository imageRepository;
    private final IssueKeyGenerator issueKeyGenerator;
    private final ProjectMemberRepository projectMemberRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Tạo file mẫu Excel (.xlsx) chuẩn Jira để người dùng điền thông tin import.
     */
    public byte[] generateIssueTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Issue Template");
            sheet.createFreezePane(0, 1);

            // Style cho header
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createBorderedStyle(workbook);

            String[] headers = {
                    "Tiêu đề công việc (*)",
                    "Mô tả chi tiết",
                    "Trạng thái",
                    "Độ ưu tiên",
                    "Email người phụ trách",
                    "Hạn chót (yyyy-MM-dd)",
                    "Nhãn (cách nhau dấu phẩy)"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dữ liệu mẫu dòng 1
            Row sampleRow1 = sheet.createRow(1);
            sampleRow1.createCell(0).setCellValue("Thiết kế giao diện trang chủ");
            sampleRow1.createCell(1).setCellValue("Chi tiết các component header, banner, footer");
            sampleRow1.createCell(2).setCellValue("TODO");
            sampleRow1.createCell(3).setCellValue("HIGH");
            sampleRow1.createCell(4).setCellValue("dev1@example.com");
            sampleRow1.createCell(5).setCellValue(LocalDate.now().plusDays(7).format(DATE_FORMATTER));
            sampleRow1.createCell(6).setCellValue("frontend, ui");

            // Dữ liệu mẫu dòng 2
            Row sampleRow2 = sheet.createRow(2);
            sampleRow2.createCell(0).setCellValue("Viết API xác thực & Captcha");
            sampleRow2.createCell(1).setCellValue("Hỗ trợ sinh mã captcha ảnh và rate limit");
            sampleRow2.createCell(2).setCellValue("IN_PROGRESS");
            sampleRow2.createCell(3).setCellValue("MEDIUM");
            sampleRow2.createCell(4).setCellValue("");
            sampleRow2.createCell(5).setCellValue(LocalDate.now().plusDays(10).format(DATE_FORMATTER));
            sampleRow2.createCell(6).setCellValue("backend, security");

            for (int r = 1; r <= 2; r++) {
                Row row = sheet.getRow(r);
                for (int c = 0; c < headers.length; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) cell.setCellStyle(dataStyle);
                }
            }

            // Data Validation: Dropdown chọn Trạng thái (Cột C - index 2)
            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            DataValidationConstraint statusConstraint = validationHelper.createExplicitListConstraint(
                    new String[]{"TODO", "IN_PROGRESS", "REVIEW", "DONE"}
            );
            CellRangeAddressList statusRange = new CellRangeAddressList(1, 1000, 2, 2);
            DataValidation statusValidation = validationHelper.createValidation(statusConstraint, statusRange);
            statusValidation.setShowErrorBox(true);
            sheet.addValidationData(statusValidation);

            // Data Validation: Dropdown chọn Độ ưu tiên (Cột D - index 3): LOW, MEDIUM, HIGH
            DataValidationConstraint priorityConstraint = validationHelper.createExplicitListConstraint(
                    new String[]{"LOW", "MEDIUM", "HIGH"}
            );
            CellRangeAddressList priorityRange = new CellRangeAddressList(1, 1000, 3, 3);
            DataValidation priorityValidation = validationHelper.createValidation(priorityConstraint, priorityRange);
            priorityValidation.setShowErrorBox(true);
            sheet.addValidationData(priorityValidation);

            // Auto-fit độ rộng cột
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 4200));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate issue template Excel", e);
            throw new RuntimeException("Không thể tạo file Excel mẫu", e);
        }
    }

    /**
     * Xuất danh sách Issue ra Excel theo 2 tùy chọn chuẩn Jira:
     * - "current": Chỉ xuất 6 cột hiển thị trên bảng
     * - "all": Xuất toàn bộ 18 trường dữ liệu chi tiết
     */
    @Transactional(readOnly = true)
    public byte[] exportIssues(Long projectId, String scope, IssueStatus status, IssuePriority priority,
                               Long assigneeId, Long labelId, String keyword, Boolean isOverdue) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        LocalDateTime now = LocalDateTime.now();
        List<Issue> issues = issueRepository.findAllByProjectWithFilters(
                projectId, status, priority, assigneeId, labelId, keyword, isOverdue, now
        );

        boolean isAllFields = "all".equalsIgnoreCase(scope);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(project.getKey() + " Issues");
            sheet.createFreezePane(0, 1);

            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle borderStyle = createBorderedStyle(workbook);
            XSSFCellStyle centerStyle = createBorderedStyle(workbook);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle dateStyle = createBorderedStyle(workbook);
            dateStyle.setAlignment(HorizontalAlignment.CENTER);

            // Priority styles
            XSSFCellStyle highPriorityStyle = createPriorityStyle(workbook, new java.awt.Color(254, 226, 226), new java.awt.Color(185, 28, 28)); // Đỏ nhạt
            XSSFCellStyle medPriorityStyle  = createPriorityStyle(workbook, new java.awt.Color(254, 243, 199), new java.awt.Color(180, 83, 9));  // Vàng nhạt
            XSSFCellStyle lowPriorityStyle  = createPriorityStyle(workbook, new java.awt.Color(224, 242, 254), new java.awt.Color(3, 105, 161)); // Xanh nhạt

            String[] headers = isAllFields ? new String[]{
                    "Issue ID", "Issue Key", "Project Key", "Project Name", "Title",
                    "Description", "Status", "Priority", "Reporter Name", "Reporter Email",
                    "Assignee Name", "Assignee Email", "Due Date", "Labels", "Created At",
                    "Updated At", "Comments Count", "Attachments Count"
            } : new String[]{
                    "Key", "Title", "Status", "Priority", "Assignee", "Due Date"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Issue issue : issues) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(20);

                if (isAllFields) {
                    long commentCount = commentRepository.countByIssueId(issue.getId());
                    long attachmentCount = imageRepository.countByEntityTypeAndEntityId(ImageEntityType.ISSUE, issue.getId());
                    String labelsStr = issue.getLabels().stream().map(Label::getName).collect(Collectors.joining(", "));

                    createCell(row, 0, String.valueOf(issue.getId()), centerStyle);
                    createCell(row, 1, issue.getIssueKey(), centerStyle);
                    createCell(row, 2, project.getKey(), centerStyle);
                    createCell(row, 3, project.getName(), borderStyle);
                    createCell(row, 4, issue.getTitle(), borderStyle);
                    createCell(row, 5, issue.getDescription() != null ? issue.getDescription() : "", borderStyle);
                    createCell(row, 6, issue.getStatus().name(), centerStyle);

                    Cell priorityCell = row.createCell(7);
                    priorityCell.setCellValue(issue.getPriority().name());
                    priorityCell.setCellStyle(getPriorityCellStyle(issue.getPriority(), highPriorityStyle, medPriorityStyle, lowPriorityStyle));

                    createCell(row, 8, issue.getReporter() != null ? issue.getReporter().getFullName() : "", borderStyle);
                    createCell(row, 9, issue.getReporter() != null ? issue.getReporter().getEmail() : "", borderStyle);
                    createCell(row, 10, issue.getAssignee() != null ? issue.getAssignee().getFullName() : "Unassigned", borderStyle);
                    createCell(row, 11, issue.getAssignee() != null ? issue.getAssignee().getEmail() : "", borderStyle);
                    createCell(row, 12, issue.getDueDate() != null ? issue.getDueDate().format(DATE_FORMATTER) : "", dateStyle);
                    createCell(row, 13, labelsStr, borderStyle);
                    createCell(row, 14, issue.getCreatedAt() != null ? issue.getCreatedAt().format(DATETIME_FORMATTER) : "", dateStyle);
                    createCell(row, 15, issue.getUpdatedAt() != null ? issue.getUpdatedAt().format(DATETIME_FORMATTER) : "", dateStyle);
                    createCell(row, 16, String.valueOf(commentCount), centerStyle);
                    createCell(row, 17, String.valueOf(attachmentCount), centerStyle);
                } else {
                    createCell(row, 0, issue.getIssueKey(), centerStyle);
                    createCell(row, 1, issue.getTitle(), borderStyle);
                    createCell(row, 2, issue.getStatus().name(), centerStyle);

                    Cell priorityCell = row.createCell(3);
                    priorityCell.setCellValue(issue.getPriority().name());
                    priorityCell.setCellStyle(getPriorityCellStyle(issue.getPriority(), highPriorityStyle, medPriorityStyle, lowPriorityStyle));

                    createCell(row, 4, issue.getAssignee() != null ? issue.getAssignee().getFullName() : "Unassigned", borderStyle);
                    createCell(row, 5, issue.getDueDate() != null ? issue.getDueDate().format(DATE_FORMATTER) : "", dateStyle);
                }
            }

            // Auto-filter
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            // Auto-width
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(currentWidth + 1000, 3600), 16000));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export issues to Excel", e);
            throw new RuntimeException("Không thể xuất file Excel", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportIssues(Long projectId, String scope, IssueStatus status, IssuePriority priority,
                               Long assigneeId, Long labelId, String keyword) {
        return exportIssues(projectId, scope, status, priority, assigneeId, labelId, keyword, null);
    }

    /**
     * Nhập danh sách Issue từ file Excel vào Project.
     * Chỉ PM của Project hoặc Admin mới có quyền import.
     */
    @Transactional
    public ImportResultDTO importIssues(Long projectId, MultipartFile file, Long requesterId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng tải lên file Excel (.xlsx)");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        User reporter = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));

        // Kiểm tra quyền: Phải là Creator của project, hoặc Member có vai trò PROJECT_MANAGER, hoặc Admin hệ thống
        boolean isCreator = project.getCreator() != null && project.getCreator().getId().equals(requesterId);
        boolean isPmMember = projectMemberRepository.findByProjectIdAndUserIdAndRoleName(projectId, requesterId, "PROJECT_MANAGER").isPresent();
        boolean isAdmin = reporter.getRole() != null && "ADMIN".equalsIgnoreCase(reporter.getRole().getName());
        if (!isCreator && !isPmMember && !isAdmin) {
            throw new UnauthorizedActionException("Chỉ Project Manager hoặc Admin mới có quyền import công việc vào dự án này");
        }

        ImportResultDTO result = ImportResultDTO.builder()
                .errors(new ArrayList<>())
                .build();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new IllegalArgumentException("File Excel không có dữ liệu để nhập");
            }

            int lastRowNum = Math.min(sheet.getLastRowNum(), 500); // Giới hạn tối đa 500 dòng
            result.setTotalRows(lastRowNum);

            // Tìm số thứ tự lớn nhất hiện tại của issue key trong project
            String prefix = project.getKey() + "-";
            List<String> existingKeys = issueRepository.findIssueKeysByPrefix(prefix);
            int currentMaxNumber = 0;
            for (String key : existingKeys) {
                if (key != null && key.startsWith(prefix)) {
                    try {
                        int num = Integer.parseInt(key.substring(prefix.length()));
                        if (num > currentMaxNumber) {
                            currentMaxNumber = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                int displayRow = r + 1; // Số thứ tự dòng trên Excel hiển thị cho user

                // 1. Tiêu đề (Bắt buộc)
                String title = getCellValueAsString(row.getCell(0));
                if (title == null || title.isBlank()) {
                    result.getErrors().add(new ImportResultDTO.RowError(displayRow, "title", "Tiêu đề công việc không được để trống"));
                    continue;
                }
                if (title.length() > 200) {
                    title = title.substring(0, 200);
                }

                // 2. Mô tả
                String description = getCellValueAsString(row.getCell(1));

                // 3. Trạng thái (Mặc định: TODO)
                String statusStr = getCellValueAsString(row.getCell(2));
                IssueStatus status = IssueStatus.TODO;
                if (statusStr != null && !statusStr.isBlank()) {
                    try {
                        status = IssueStatus.valueOf(statusStr.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "status",
                                "Trạng thái không hợp lệ (hợp lệ: TODO, IN_PROGRESS, REVIEW, DONE)"));
                        continue;
                    }
                }

                // 4. Độ ưu tiên (Mặc định: MEDIUM; chỉ LOW, MEDIUM, HIGH)
                String priorityStr = getCellValueAsString(row.getCell(3));
                IssuePriority priority = IssuePriority.MEDIUM;
                if (priorityStr != null && !priorityStr.isBlank()) {
                    try {
                        priority = IssuePriority.valueOf(priorityStr.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "priority",
                                "Độ ưu tiên không hợp lệ (hợp lệ: LOW, MEDIUM, HIGH)"));
                        continue;
                    }
                }

                // 5. Assignee qua Email
                String assigneeEmail = getCellValueAsString(row.getCell(4));
                User assignee = null;
                if (assigneeEmail != null && !assigneeEmail.isBlank()) {
                    Optional<User> userOpt = userRepository.findByEmail(assigneeEmail.trim());
                    if (userOpt.isEmpty()) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "assignee",
                                "Email người phụ trách không tồn tại trong hệ thống: " + assigneeEmail));
                        continue;
                    }
                    assignee = userOpt.get();
                    if (assignee.isLocked() || assignee.getDeletedAt() != null) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "assignee",
                                "Tài khoản người phụ trách đã bị khóa hoặc vô hiệu hóa"));
                        continue;
                    }
                }

                // 6. Hạn chót (Due Date)
                LocalDateTime dueDate = parseDueDate(row.getCell(5));

                // 7. Nhãn (Labels)
                String labelsStr = getCellValueAsString(row.getCell(6));
                Set<Label> labels = parseLabels(labelsStr);

                // Sinh issueKey tuần tự không bị trùng lặp
                currentMaxNumber++;
                String issueKey = prefix + currentMaxNumber;

                Issue issue = Issue.builder()
                        .issueKey(issueKey)
                        .project(project)
                        .reporter(reporter)
                        .assignee(assignee)
                        .title(title.trim())
                        .description(description != null ? description.trim() : null)
                        .status(status)
                        .priority(priority)
                        .dueDate(dueDate)
                        .labels(labels)
                        .build();

                try {
                    issueRepository.save(issue);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception ex) {
                    log.error("Failed to save imported issue at row {}: {}", displayRow, ex.getMessage());
                    result.getErrors().add(new ImportResultDTO.RowError(displayRow, "database", "Lỗi lưu vào CSDL: " + ex.getMessage()));
                    currentMaxNumber--;
                }
            }

            result.setFailedCount(result.getErrors().size());
            log.info("Imported {} issues to project [{}] with {} errors",
                    result.getSuccessCount(), project.getKey(), result.getFailedCount());

            return result;
        } catch (IllegalArgumentException | UnauthorizedActionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error importing issues from Excel", e);
            throw new IllegalArgumentException("Lỗi xử lý file Excel: " + (e.getMessage() != null ? e.getMessage() : "Định dạng không hợp lệ"), e);
        }
    }

    // ----------------------------------------------------------------
    // Helper Methods
    // ----------------------------------------------------------------

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle getPriorityCellStyle(IssuePriority priority, XSSFCellStyle high, XSSFCellStyle med, XSSFCellStyle low) {
        return switch (priority) {
            case HIGH -> high;
            case MEDIUM -> med;
            case LOW -> low;
        };
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new java.awt.Color(255, 255, 255), new DefaultIndexedColorMap()));
        style.setFont(font);

        style.setFillForegroundColor(new XSSFColor(new java.awt.Color(0, 82, 204), new DefaultIndexedColorMap())); // Jira Blue #0052CC
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style, IndexedColors.GREY_40_PERCENT.getIndex());
        return style;
    }

    private XSSFCellStyle createBorderedStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style, IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private XSSFCellStyle createPriorityStyle(XSSFWorkbook workbook, java.awt.Color bgColor, java.awt.Color textColor) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(new XSSFColor(textColor, new DefaultIndexedColorMap()));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(bgColor, new DefaultIndexedColorMap()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style, IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private void setBorders(CellStyle style, short borderColor) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(borderColor);
        style.setBottomBorderColor(borderColor);
        style.setLeftBorderColor(borderColor);
        style.setRightBorderColor(borderColor);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null || row.getFirstCellNum() < 0) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValueAsString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num)) {
                        yield String.valueOf((long) num);
                    }
                    yield String.valueOf(num);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private LocalDateTime parseDueDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }
            String str = getCellValueAsString(cell).trim();
            if (str.isBlank()) return null;
            if (str.length() == 10) {
                return LocalDate.parse(str, DATE_FORMATTER).atTime(23, 59, 59);
            }
            return LocalDateTime.parse(str, DATETIME_FORMATTER);
        } catch (Exception e) {
            log.debug("Could not parse due date from cell, ignoring: {}", cell);
            return null;
        }
    }

    private Set<Label> parseLabels(String labelsStr) {
        if (labelsStr == null || labelsStr.isBlank()) {
            return new HashSet<>();
        }
        Set<Label> result = new HashSet<>();
        String[] parts = labelsStr.split("[,;]");
        for (String p : parts) {
            String name = p.trim();
            if (!name.isEmpty()) {
                Label label = labelRepository.findByName(name).orElseGet(() -> {
                    Label newLabel = Label.builder().name(name).build();
                    return labelRepository.save(newLabel);
                });
                result.add(label);
            }
        }
        return result;
    }
}
