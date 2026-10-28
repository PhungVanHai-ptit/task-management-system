package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.ReportDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.enums.IssueStatus;
import com.intern.taskmanagementsystem.repository.IssueRepository;
import com.intern.taskmanagementsystem.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.repository.IssueChangeLogRepository;
import com.intern.taskmanagementsystem.repository.ProjectRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ReportService: thống kê, báo cáo.
 * Demo kiến thức:
 *  - Stream.collect(Collectors.groupingBy(...)) cho dashboard  [collections]
 *  - Native query cho top N overdue users                      [nativequery]
 *  - HQL/JPQL cho activity report                             [hql]
 *  - StringBuilder khi build chuỗi audit log nhiều bước       [string-vs-stringbuilder]
 */
import com.intern.taskmanagementsystem.entity.IssueChangeLog;
import com.intern.taskmanagementsystem.entity.ProjectMember;
import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.repository.ProjectMemberRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueChangeLogRepository issueChangeLogRepository;
    private final UserMapper userMapper;


    /**
     * Lấy tổng quan Dashboard: 4 thẻ KPI toàn hệ thống + Top 10 dự án cho biểu đồ cột (hỗ trợ lọc scope và timeRange).
     */
    public ReportDTO.DashboardSummaryResponse getDashboardSummary(Long userId, String scope, String timeRange) {
        List<Project> projects = getProjectsByScope(userId, scope);
        LocalDateTime startDate = calculateStartDate(timeRange);

        if (projects.isEmpty()) {
            return ReportDTO.DashboardSummaryResponse.builder()
                    .totalProjects(0)
                    .totalTodo(0)
                    .totalInProgress(0)
                    .totalReview(0)
                    .totalDone(0)
                    .totalIssues(0)
                    .chartProjects(List.of())
                    .build();
        }

        List<ReportDTO.ProjectDistributionResponse> distributionList = buildDistributionList(projects, startDate);

        long totalProjects = distributionList.size();
        long totalTodo = distributionList.stream().mapToLong(ReportDTO.ProjectDistributionResponse::getTodoCount).sum();
        long totalInProgress = distributionList.stream().mapToLong(ReportDTO.ProjectDistributionResponse::getInProgressCount).sum();
        long totalReview = distributionList.stream().mapToLong(ReportDTO.ProjectDistributionResponse::getReviewCount).sum();
        long totalDone = distributionList.stream().mapToLong(ReportDTO.ProjectDistributionResponse::getDoneCount).sum();
        long totalIssues = totalTodo + totalInProgress + totalReview + totalDone;

        // Giới hạn biểu đồ ở top 8-10 project (sắp xếp theo tổng issue giảm dần, sau đó theo ID dự án)
        List<ReportDTO.ProjectDistributionResponse> chartProjects = distributionList.stream()
                .sorted(Comparator.comparingLong(ReportDTO.ProjectDistributionResponse::getTotalCount).reversed()
                        .thenComparing(ReportDTO.ProjectDistributionResponse::getProjectId))
                .limit(10)
                .toList();

        return ReportDTO.DashboardSummaryResponse.builder()
                .totalProjects(totalProjects)
                .totalTodo(totalTodo)
                .totalInProgress(totalInProgress)
                .totalReview(totalReview)
                .totalDone(totalDone)
                .totalIssues(totalIssues)
                .chartProjects(chartProjects)
                .build();
    }

    /**
     * Lấy danh sách phân bố issue theo dự án có phân trang cho bảng dữ liệu (hỗ trợ lọc scope và timeRange).
     */
    public Page<ReportDTO.ProjectDistributionResponse> getProjectDistributions(
            Long userId, String scope, String timeRange, Pageable pageable) {
        List<Project> projects = getProjectsByScope(userId, scope);
        LocalDateTime startDate = calculateStartDate(timeRange);

        if (projects.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<ReportDTO.ProjectDistributionResponse> distributionList = buildDistributionList(projects, startDate);

        int start = (int) pageable.getOffset();
        if (start >= distributionList.size()) {
            return new PageImpl<>(List.of(), pageable, distributionList.size());
        }
        int end = Math.min(start + pageable.getPageSize(), distributionList.size());
        List<ReportDTO.ProjectDistributionResponse> pageContent = distributionList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, distributionList.size());
    }

    private List<ReportDTO.ProjectDistributionResponse> buildDistributionList(List<Project> projects, LocalDateTime startDate) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        if (projectIds.isEmpty()) {
            return List.of();
        }

        List<ReportDTO.ProjectStatusCount> statusCounts = issueRepository.countIssuesByProjectAndStatus(projectIds, startDate);

        // Gom nhóm kết quả theo projectId bằng Collectors.groupingBy
        Map<Long, Map<IssueStatus, Long>> countsByProject = statusCounts.stream()
                .collect(Collectors.groupingBy(
                        ReportDTO.ProjectStatusCount::getProjectId,
                        Collectors.toMap(
                                ReportDTO.ProjectStatusCount::getStatus,
                                ReportDTO.ProjectStatusCount::getCount,
                                (v1, v2) -> v1
                        )
                ));

        return projects.stream().map(project -> {
            Map<IssueStatus, Long> counts = countsByProject.getOrDefault(project.getId(), Map.of());
            long todo = counts.getOrDefault(IssueStatus.TODO, 0L);
            long inProgress = counts.getOrDefault(IssueStatus.IN_PROGRESS, 0L);
            long review = counts.getOrDefault(IssueStatus.REVIEW, 0L);
            long done = counts.getOrDefault(IssueStatus.DONE, 0L);
            long total = todo + inProgress + review + done;
            double completionRate = (total == 0) ? 0.0 : Math.round((done * 100.0 / total) * 10.0) / 10.0;

            return ReportDTO.ProjectDistributionResponse.builder()
                    .projectId(project.getId())
                    .projectKey(project.getKey())
                    .projectName(project.getName())
                    .todoCount(todo)
                    .inProgressCount(inProgress)
                    .reviewCount(review)
                    .doneCount(done)
                    .totalCount(total)
                    .completionRate(completionRate)
                    .build();
        }).toList();
    }

    /**
     * Lấy toàn bộ project có liên quan đến user:
     * - Admin: Toàn bộ project trong hệ thống
     * - User thường: Các project user tham gia (thành viên), do user tạo, hoặc có issue được giao cho user
     */
    private List<Project> getUserRelevantProjects(Long userId) {
        if (isAdmin(userId)) {
            return projectRepository.findAll();
        }
        Map<Long, Project> projectMap = new LinkedHashMap<>();
        projectRepository.findAllByMemberUserId(userId).forEach(p -> projectMap.put(p.getId(), p));
        projectRepository.findAllCreatedByUserId(userId).forEach(p -> projectMap.put(p.getId(), p));
        projectRepository.findAllWithIssuesAssignedToUser(userId).forEach(p -> projectMap.put(p.getId(), p));
        return new ArrayList<>(projectMap.values());
    }

    private List<Project> getProjectsByScope(Long userId, String scope) {
        if (scope == null) scope = "ALL";

        switch (scope.toUpperCase()) {
            case "MANAGED":
                return projectRepository.findAllCreatedByUserId(userId);
            case "ASSIGNED":
                return projectRepository.findAllWithIssuesAssignedToUser(userId);
            case "ALL":
            default:
                return getUserRelevantProjects(userId);
        }
    }

    private LocalDateTime calculateStartDate(String timeRange) {
        if (timeRange == null || "ALL_TIME".equalsIgnoreCase(timeRange)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        switch (timeRange.toUpperCase()) {
            case "THIS_WEEK":
                // Bắt đầu từ Thứ Hai của tuần hiện tại
                return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case "THIS_MONTH":
                // Bắt đầu từ ngày 1 của tháng hiện tại
                return today.withDayOfMonth(1).atStartOfDay();
            case "THIS_QUARTER":
                // Bắt đầu từ ngày 1 của tháng đầu quý
                int firstMonthOfQuarter = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                return today.withMonth(firstMonthOfQuarter).withDayOfMonth(1).atStartOfDay();
            default:
                return null;
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getName()))
                .orElse(false);
    }

    // =========================================================================
    // STATUS TRANSITION ANALYTICS (6 CHỈ SỐ THỜI GIAN CHUYỂN TRẠNG THÁI)
    // =========================================================================

    /**
     * Lấy tóm tắt 6 chỉ số thời gian chuyển trạng thái cho dự án được chọn (hoặc toàn bộ scope)
     * kèm so sánh Benchmark với toàn bộ hệ thống.
     */
    public ReportDTO.TransitionSummaryResponse getTransitionSummary(Long userId, Long projectId, String scope, String timeRange) {
        List<Project> accessibleProjects = getProjectsByScope(userId, scope);
        if (accessibleProjects.isEmpty()) {
            return buildEmptyTransitionSummary(null, null);
        }

        Project selectedProject = null;
        List<Long> targetProjectIds;

        if (projectId != null) {
            selectedProject = accessibleProjects.stream()
                    .filter(p -> p.getId().equals(projectId))
                    .findFirst()
                    .orElse(null);
            if (selectedProject == null && isAdmin(userId)) {
                selectedProject = projectRepository.findById(projectId).orElse(null);
            }
            if (selectedProject == null) {
                return buildEmptyTransitionSummary(null, null);
            }
            targetProjectIds = List.of(selectedProject.getId());
        } else {
            targetProjectIds = accessibleProjects.stream().map(Project::getId).toList();
        }

        LocalDateTime startDate = calculateStartDate(timeRange);

        // 1. Thống kê dự án mục tiêu
        List<Object[]> rows = issueChangeLogRepository.aggregateTransitionsByProjectIds(targetProjectIds, startDate);
        Map<String, MetricHolder> targetMetrics = parseTransitionRows(rows);

        // 2. Thống kê Benchmark toàn hệ thống:
        // - Admin: Tính trên toàn bộ project trong hệ thống
        // - User thường: Chỉ tính trên các project có liên quan đến user (tham gia / tạo / được giao task)
        List<Project> systemBenchmarkProjects = getUserRelevantProjects(userId);
        List<Long> systemBenchmarkProjectIds = systemBenchmarkProjects.stream().map(Project::getId).toList();
        List<Object[]> systemRows = systemBenchmarkProjectIds.isEmpty() ? List.of() :
                issueChangeLogRepository.aggregateTransitionsByProjectIds(systemBenchmarkProjectIds, startDate);
        Map<String, MetricHolder> systemMetrics = parseTransitionRows(systemRows);

        long totalTransitions = targetMetrics.values().stream().mapToLong(MetricHolder::count).sum();
        long reworkTransitions =
                getMetricCount(targetMetrics, "REVIEW", "IN_PROGRESS") +
                getMetricCount(targetMetrics, "DONE", "REVIEW") +
                getMetricCount(targetMetrics, "IN_PROGRESS", "TODO");

        double reworkRate = totalTransitions > 0
                ? Math.round((reworkTransitions * 100.0 / totalTransitions) * 10.0) / 10.0
                : 0.0;

        return ReportDTO.TransitionSummaryResponse.builder()
                .selectedProjectId(selectedProject != null ? selectedProject.getId() : null)
                .selectedProjectName(selectedProject != null ? selectedProject.getName() : null)
                .todoToProgress(toTransitionMetric(targetMetrics, "TODO", "IN_PROGRESS"))
                .progressToReview(toTransitionMetric(targetMetrics, "IN_PROGRESS", "REVIEW"))
                .reviewToDone(toTransitionMetric(targetMetrics, "REVIEW", "DONE"))
                .doneToReview(toTransitionMetric(targetMetrics, "DONE", "REVIEW"))
                .reviewToProgress(toTransitionMetric(targetMetrics, "REVIEW", "IN_PROGRESS"))
                .progressToTodo(toTransitionMetric(targetMetrics, "IN_PROGRESS", "TODO"))
                .systemTodoToProgress(toTransitionMetric(systemMetrics, "TODO", "IN_PROGRESS"))
                .systemProgressToReview(toTransitionMetric(systemMetrics, "IN_PROGRESS", "REVIEW"))
                .systemReviewToDone(toTransitionMetric(systemMetrics, "REVIEW", "DONE"))
                .systemDoneToReview(toTransitionMetric(systemMetrics, "DONE", "REVIEW"))
                .systemReviewToProgress(toTransitionMetric(systemMetrics, "REVIEW", "IN_PROGRESS"))
                .systemProgressToTodo(toTransitionMetric(systemMetrics, "IN_PROGRESS", "TODO"))
                .totalTransitions(totalTransitions)
                .totalReworkTransitions(reworkTransitions)
                .reworkRate(reworkRate)
                .build();
    }

    /**
     * Biểu đồ xu hướng thời gian chuyển trạng thái theo tháng (N tháng gần nhất hoặc tùy chọn startDate - endDate).
     */

    public List<ReportDTO.MonthlyTransitionTrendResponse> getMonthlyTransitionTrends(
            Long userId, Long projectId, String scope, Integer months, LocalDate startDate, LocalDate endDate) {
        List<Project> accessibleProjects = getProjectsByScope(userId, scope);
        if (accessibleProjects.isEmpty()) {
            return List.of();
        }

        List<Long> targetProjectIds;
        if (projectId != null) {
            targetProjectIds = accessibleProjects.stream()
                    .filter(p -> p.getId().equals(projectId))
                    .map(Project::getId)
                    .toList();
            if (targetProjectIds.isEmpty() && isAdmin(userId)) {
                if (projectRepository.existsById(projectId)) {
                    targetProjectIds = List.of(projectId);
                }
            }
        } else {
            targetProjectIds = accessibleProjects.stream().map(Project::getId).toList();
        }

        if (targetProjectIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime startDateTime;
        LocalDateTime endDateTime;

        if (startDate != null && endDate != null) {
            LocalDate s = startDate.isAfter(endDate) ? endDate : startDate;
            LocalDate e = startDate.isAfter(endDate) ? startDate : endDate;
            startDateTime = s.withDayOfMonth(1).atStartOfDay();
            endDateTime = e.atTime(java.time.LocalTime.MAX);
        } else if (startDate != null) {
            startDateTime = startDate.withDayOfMonth(1).atStartOfDay();
            endDateTime = LocalDateTime.now();
        } else {
            int monthCount = (months != null && months > 0 && months <= 36) ? months : 6;
            startDateTime = LocalDate.now().minusMonths(monthCount - 1).withDayOfMonth(1).atStartOfDay();
            endDateTime = LocalDateTime.now();
        }

        List<Object[]> rawRows = issueChangeLogRepository.aggregateMonthlyTransitionsByProjectIds(targetProjectIds, startDateTime, endDateTime);

        // Group rows by period (YYYY-MM)
        Map<String, Map<String, MetricHolder>> byPeriod = new TreeMap<>();
        for (Object[] r : rawRows) {
            if (r.length < 5 || r[0] == null) continue;
            String period = r[0].toString();
            String oldStatus = r[1] != null ? r[1].toString() : "";
            String newStatus = r[2] != null ? r[2].toString() : "";
            Double avg = parseDouble(r[3]);
            Long cnt = parseLong(r[4]);

            String key = oldStatus + "->" + newStatus;
            byPeriod.computeIfAbsent(period, k -> new HashMap<>()).put(key, new MetricHolder(avg, cnt));
        }

        List<ReportDTO.MonthlyTransitionTrendResponse> result = new ArrayList<>();
        LocalDate cur = startDateTime.toLocalDate();
        LocalDate endLimit = endDateTime.toLocalDate();

        while (!cur.isAfter(endLimit)) {
            String period = cur.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            Map<String, MetricHolder> metrics = byPeriod.getOrDefault(period, Map.of());

            long totalCnt = metrics.values().stream().mapToLong(MetricHolder::count).sum();

            result.add(ReportDTO.MonthlyTransitionTrendResponse.builder()
                    .period(period)
                    .todoToProgressAvg(getMetricAvg(metrics, "TODO", "IN_PROGRESS"))
                    .progressToReviewAvg(getMetricAvg(metrics, "IN_PROGRESS", "REVIEW"))
                    .reviewToDoneAvg(getMetricAvg(metrics, "REVIEW", "DONE"))
                    .doneToReviewAvg(getMetricAvg(metrics, "DONE", "REVIEW"))
                    .reviewToProgressAvg(getMetricAvg(metrics, "REVIEW", "IN_PROGRESS"))
                    .progressToTodoAvg(getMetricAvg(metrics, "IN_PROGRESS", "TODO"))
                    .transitionCount(totalCnt)
                    .build());

            cur = cur.plusMonths(1);
        }

        return result;
    }

    /**
     * Bảng danh sách dự án với thống kê chi tiết 6 chỉ số, hỗ trợ tìm kiếm và lọc cặp trạng thái A -> B.
     */
    public Page<ReportDTO.ProjectTransitionStatsResponse> getProjectTransitionStats(
            Long userId, String scope, String timeRange, String search, String fromStatus, String toStatus, Pageable pageable) {
        List<Project> projects = getProjectsByScope(userId, scope);
        if (projects.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        if (search != null && !search.trim().isEmpty()) {
            String term = search.trim().toLowerCase();
            projects = projects.stream()
                    .filter(p -> (p.getKey() != null && p.getKey().toLowerCase().contains(term)) ||
                                 (p.getName() != null && p.getName().toLowerCase().contains(term)))
                    .toList();
        }

        if (projects.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        LocalDateTime startDate = calculateStartDate(timeRange);

        List<Object[]> perProjectRows = issueChangeLogRepository.aggregateTransitionsPerProject(projectIds, startDate);

        Map<Long, Map<String, MetricHolder>> byProject = new HashMap<>();
        for (Object[] r : perProjectRows) {
            if (r.length < 5 || r[0] == null) continue;
            Long pId = parseLong(r[0]);
            String oldStatus = r[1] != null ? r[1].toString() : "";
            String newStatus = r[2] != null ? r[2].toString() : "";
            Double avg = parseDouble(r[3]);
            Long cnt = parseLong(r[4]);

            byProject.computeIfAbsent(pId, k -> new HashMap<>()).put(oldStatus + "->" + newStatus, new MetricHolder(avg, cnt));
        }

        String filterStep = (fromStatus != null && toStatus != null && !fromStatus.isBlank() && !toStatus.isBlank())
                ? (fromStatus.trim().toUpperCase() + "->" + toStatus.trim().toUpperCase())
                : null;

        List<ReportDTO.ProjectTransitionStatsResponse> responseList = new ArrayList<>();
        for (Project p : projects) {
            Map<String, MetricHolder> m = byProject.getOrDefault(p.getId(), Map.of());

            if (filterStep != null) {
                MetricHolder stepMetric = m.get(filterStep);
                if (stepMetric == null || stepMetric.count() == 0) {
                    continue;
                }
            }

            long totalTransitions = m.values().stream().mapToLong(MetricHolder::count).sum();
            long reworkCount = getMetricCount(m, "REVIEW", "IN_PROGRESS") +
                               getMetricCount(m, "DONE", "REVIEW") +
                               getMetricCount(m, "IN_PROGRESS", "TODO");
            double reworkRate = totalTransitions > 0
                    ? Math.round((reworkCount * 100.0 / totalTransitions) * 10.0) / 10.0
                    : 0.0;

            responseList.add(ReportDTO.ProjectTransitionStatsResponse.builder()
                    .projectId(p.getId())
                    .projectKey(p.getKey())
                    .projectName(p.getName())
                    .todoToProgress(toTransitionMetric(m, "TODO", "IN_PROGRESS"))
                    .progressToReview(toTransitionMetric(m, "IN_PROGRESS", "REVIEW"))
                    .reviewToDone(toTransitionMetric(m, "REVIEW", "DONE"))
                    .doneToReview(toTransitionMetric(m, "DONE", "REVIEW"))
                    .reviewToProgress(toTransitionMetric(m, "REVIEW", "IN_PROGRESS"))
                    .progressToTodo(toTransitionMetric(m, "IN_PROGRESS", "TODO"))
                    .totalTransitions(totalTransitions)
                    .reworkCount(reworkCount)
                    .reworkRate(reworkRate)
                    .build());
        }

        int totalElements = responseList.size();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), totalElements);

        List<ReportDTO.ProjectTransitionStatsResponse> pageContent = (start <= end)
                ? responseList.subList(start, end)
                : List.of();

        return new PageImpl<>(pageContent, pageable, totalElements);
    }

    /**
     * Lấy danh sách lịch sử các lần chuyển trạng thái issue chi tiết.
     * Hỗ trợ lọc theo:
     * - projectId: cụ thể hoặc null (lấy tất cả project liên quan đến user, admin lấy toàn bộ)
     * - changedById: lọc theo ID người thực hiện (null = tất cả)
     * - startDate, endDate: khoảng thời gian phát sinh chuyển trạng thái
     * - fromStatus, toStatus: bước chuyển trạng thái (hoặc lọc theo step)
     * - search: tìm kiếm theo key hoặc tiêu đề issue
     * - pageable: phân trang & sắp xếp động
     */
    public Page<ReportDTO.IssueTransitionLogResponse> getIssueTransitionLogs(
            Long userId, Long projectId, Long changedById, LocalDate startDate, LocalDate endDate,
            String fromStatus, String toStatus, String search, Pageable pageable) {

        List<Long> targetProjectIds;
        if (projectId != null) {
            boolean hasAccess = isAdmin(userId)
                    ? projectRepository.existsById(projectId)
                    : getUserRelevantProjects(userId).stream().anyMatch(p -> p.getId().equals(projectId));
            if (!hasAccess) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            targetProjectIds = List.of(projectId);
        } else {
            List<Project> relevantProjects = getUserRelevantProjects(userId);
            if (relevantProjects.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            targetProjectIds = relevantProjects.stream().map(Project::getId).toList();
        }

        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        String cleanFromStatus = (fromStatus != null && !fromStatus.isBlank()) ? fromStatus.trim().toUpperCase() : null;
        String cleanToStatus = (toStatus != null && !toStatus.isBlank()) ? toStatus.trim().toUpperCase() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<IssueChangeLog> logPage = issueChangeLogRepository.findTransitionLogs(
                targetProjectIds, changedById, startDateTime, endDateTime, cleanFromStatus, cleanToStatus, cleanSearch, pageable
        );

        List<ReportDTO.IssueTransitionLogResponse> dtoList = logPage.getContent().stream().map(logItem -> {
            Issue issue = logItem.getIssue();
            Project project = issue != null ? issue.getProject() : null;
            Long durationSeconds = logItem.getTransitionTimeMinutes(); // Lưu số giây

            return ReportDTO.IssueTransitionLogResponse.builder()
                    .id(logItem.getId())
                    .issueId(issue != null ? issue.getId() : null)
                    .issueKey(issue != null ? issue.getIssueKey() : null)
                    .issueTitle(issue != null ? issue.getTitle() : null)
                    .projectId(project != null ? project.getId() : null)
                    .projectKey(project != null ? project.getKey() : null)
                    .projectName(project != null ? project.getName() : null)
                    .fromStatus(logItem.getOldValue())
                    .toStatus(logItem.getNewValue())
                    .durationSeconds(durationSeconds)
                    .formattedDuration(DateUtils.formatDurationSeconds(durationSeconds))
                    .changedAt(logItem.getChangedAt())
                    .changedBy(logItem.getChangedBy() != null ? userMapper.toUserSummaryResponse(logItem.getChangedBy()) : null)
                    .build();
        }).toList();

        return new PageImpl<>(dtoList, pageable, logPage.getTotalElements());
    }

    // =========================================================================
    // HELPER METHODS FOR TRANSITIONS
    // =========================================================================

    private record MetricHolder(Double avgMinutes, Long count) {}

    private Map<String, MetricHolder> parseTransitionRows(List<Object[]> rows) {
        Map<String, MetricHolder> map = new HashMap<>();
        if (rows == null) return map;
        for (Object[] r : rows) {
            if (r.length < 4 || r[0] == null || r[1] == null) continue;
            String oldStatus = r[0].toString();
            String newStatus = r[1].toString();
            Double avg = parseDouble(r[2]);
            Long cnt = parseLong(r[3]);
            map.put(oldStatus + "->" + newStatus, new MetricHolder(avg, cnt));
        }
        return map;
    }

    private ReportDTO.TransitionMetric toTransitionMetric(Map<String, MetricHolder> map, String from, String to) {
        String key = from + "->" + to;
        MetricHolder holder = map.get(key);
        double avg = (holder != null && holder.avgMinutes() != null)
                ? Math.round(holder.avgMinutes() * 10.0) / 10.0
                : 0.0;
        long cnt = (holder != null && holder.count() != null) ? holder.count() : 0L;
        String formatted = cnt > 0 ? DateUtils.formatDurationSeconds(Math.round(avg)) : "0s";

        return ReportDTO.TransitionMetric.builder()
                .avgMinutes(Math.round((avg / 60.0) * 10.0) / 10.0)
                .avgSeconds(avg)
                .formattedTime(formatted)
                .count(cnt)
                .build();
    }

    private Double getMetricAvg(Map<String, MetricHolder> map, String from, String to) {
        String key = from + "->" + to;
        MetricHolder holder = map.get(key);
        if (holder == null || holder.avgMinutes() == null) return 0.0;
        return Math.round(holder.avgMinutes() * 10.0) / 10.0;
    }

    private long getMetricCount(Map<String, MetricHolder> map, String from, String to) {
        String key = from + "->" + to;
        MetricHolder holder = map.get(key);
        return (holder != null && holder.count() != null) ? holder.count() : 0L;
    }

    private ReportDTO.TransitionSummaryResponse buildEmptyTransitionSummary(Long projectId, String projectName) {
        ReportDTO.TransitionMetric emptyMetric = ReportDTO.TransitionMetric.builder()
                .avgMinutes(0.0)
                .avgSeconds(0.0)
                .formattedTime("0s")
                .count(0L)
                .build();
        return ReportDTO.TransitionSummaryResponse.builder()
                .selectedProjectId(projectId)
                .selectedProjectName(projectName)
                .todoToProgress(emptyMetric)
                .progressToReview(emptyMetric)
                .reviewToDone(emptyMetric)
                .doneToReview(emptyMetric)
                .reviewToProgress(emptyMetric)
                .progressToTodo(emptyMetric)
                .systemTodoToProgress(emptyMetric)
                .systemProgressToReview(emptyMetric)
                .systemReviewToDone(emptyMetric)
                .systemDoneToReview(emptyMetric)
                .systemReviewToProgress(emptyMetric)
                .systemProgressToTodo(emptyMetric)
                .totalTransitions(0L)
                .totalReworkTransitions(0L)
                .reworkRate(0.0)
                .build();
    }

    /**
     * Lấy danh sách thành viên thực hiện các lần chuyển trạng thái để phục vụ bộ lọc người thực hiện.
     * - Nếu là ADMIN: Toàn bộ user trong hệ thống (hoặc thành viên của dự án nếu chọn projectId).
     * - Nếu là User thường: Thành viên của dự án được chọn, hoặc tất cả thành viên thuộc các dự án liên quan đến user.
     */
    public List<UserDTO.SummaryResponse> getTransitionPerformers(Long userId, Long projectId) {
        boolean isAdmin = isAdmin(userId);

        if (projectId != null) {
            if (!isAdmin) {
                boolean hasAccess = getUserRelevantProjects(userId).stream()
                        .anyMatch(p -> p.getId().equals(projectId));
                if (!hasAccess) {
                    return List.of();
                }
            }
            return projectMemberRepository.findByProjectId(projectId).stream()
                    .map(ProjectMember::getUser)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(userMapper::toUserSummaryResponse)
                    .sorted(Comparator.comparing(u -> u.getFullName() != null ? u.getFullName().toLowerCase() : u.getEmail().toLowerCase()))
                    .toList();
        }

        if (isAdmin) {
            return userRepository.findAll().stream()
                    .map(userMapper::toUserSummaryResponse)
                    .sorted(Comparator.comparing(u -> u.getFullName() != null ? u.getFullName().toLowerCase() : u.getEmail().toLowerCase()))
                    .toList();
        }

        List<Project> relevantProjects = getUserRelevantProjects(userId);
        if (relevantProjects.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = relevantProjects.stream().map(Project::getId).toList();
        return projectMemberRepository.findByProjectIdIn(projectIds).stream()
                .map(ProjectMember::getUser)
                .filter(Objects::nonNull)
                .distinct()
                .map(userMapper::toUserSummaryResponse)
                .sorted(Comparator.comparing(u -> u.getFullName() != null ? u.getFullName().toLowerCase() : u.getEmail().toLowerCase()))
                .toList();
    }

    private Double parseDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Long parseLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}
