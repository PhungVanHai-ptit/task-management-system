package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.ReportDTO;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Report endpoints — /api/reports
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** Dashboard: Thống kê tổng hợp toàn hệ thống & top 10 project cho biểu đồ (hỗ trợ lọc scope và timeRange) */
    @GetMapping("/dashboard-summary")
    public ResponseEntity<ReportDTO.DashboardSummaryResponse> getDashboardSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "ALL_TIME") String timeRange
    ) {
        return ResponseEntity.ok(reportService.getDashboardSummary(userDetails.getId(), scope, timeRange));
    }

    /** Dashboard: Phân bố issue theo từng project có phân trang (hỗ trợ lọc scope và timeRange) */
    @GetMapping("/projects-distribution")
    public ResponseEntity<Page<ReportDTO.ProjectDistributionResponse>> getProjectsDistribution(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "ALL_TIME") String timeRange,
            @PageableDefault(size = 5) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getProjectDistributions(userDetails.getId(), scope, timeRange, pageable));
    }


    /**
     * Thống kê 6 chỉ số thời gian chuyển trạng thái (Project vs Toàn hệ thống Benchmark)
     */
    @GetMapping("/transitions/summary")
    public ResponseEntity<ReportDTO.TransitionSummaryResponse> getTransitionSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "ALL_TIME") String timeRange
    ) {
        return ResponseEntity.ok(reportService.getTransitionSummary(userDetails.getId(), projectId, scope, timeRange));
    }

    /**
     * Thống kê thời gian chuyển trạng thái theo tháng (xu hướng N tháng gần nhất hoặc khoảng ngày tùy chọn startDate - endDate)
     */
    @GetMapping("/transitions/monthly-trends")
    public ResponseEntity<List<ReportDTO.MonthlyTransitionTrendResponse>> getMonthlyTransitionTrends(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.getMonthlyTransitionTrends(
                userDetails.getId(), projectId, scope, months, startDate, endDate
        ));
    }

    /**
     * Danh sách dự án với 6 chỉ số thời gian chuyển trạng thái, hỗ trợ tìm kiếm và lọc cặp trạng thái A -> B
     */
    @GetMapping("/transitions/projects")
    public ResponseEntity<Page<ReportDTO.ProjectTransitionStatsResponse>> getProjectTransitionStats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "ALL_TIME") String timeRange,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String fromStatus,
            @RequestParam(required = false) String toStatus,
            @PageableDefault(size = 5) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getProjectTransitionStats(
                userDetails.getId(), scope, timeRange, search, fromStatus, toStatus, pageable));
    }

    /**
     * Lấy danh sách lịch sử chuyển trạng thái issue chi tiết có phân trang, sắp xếp và bộ lọc (dự án, người thực hiện, ngày A - B, 6 bước chuyển, tìm kiếm).
     */
    @GetMapping("/transitions/logs")
    public ResponseEntity<Page<ReportDTO.IssueTransitionLogResponse>> getIssueTransitionLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long changedById,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String fromStatus,
            @RequestParam(required = false) String toStatus,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "changedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getIssueTransitionLogs(
                userDetails.getId(), projectId, changedById, startDate, endDate, fromStatus, toStatus, search, pageable
        ));
    }

    /**
     * Lấy danh sách thành viên thực hiện để lọc (chỉ hiện thành viên cùng project, admin hiện hết).
     */
    @GetMapping("/transitions/performers")
    public ResponseEntity<List<com.intern.taskmanagementsystem.dto.UserDTO.SummaryResponse>> getTransitionPerformers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long projectId
    ) {
        return ResponseEntity.ok(reportService.getTransitionPerformers(userDetails.getId(), projectId));
    }
}
