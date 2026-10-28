package com.intern.taskmanagementsystem.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Utility class cho xử lý ngày tháng.
 * Demo kiến thức: static method, utility class pattern.
 */
public final class DateUtils {

    private static final DateTimeFormatter WEEK_FORMATTER =
            DateTimeFormatter.ofPattern("YYYY-'W'ww", Locale.UK);
    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM");

    // Prevent instantiation — utility class
    private DateUtils() {}

    /**
     * Trả về chuỗi tuần theo format ISO: "2025-W03"
     */
    public static String toWeekLabel(LocalDateTime dateTime) {
        return dateTime.format(WEEK_FORMATTER);
    }

    /**
     * Trả về chuỗi tháng theo format: "2025-01"
     */
    public static String toMonthLabel(LocalDateTime dateTime) {
        return dateTime.format(MONTH_FORMATTER);
    }

    /**
     * Tính thời điểm X giờ sau hiện tại.
     */
    public static LocalDateTime hoursFromNow(long hours) {
        return LocalDateTime.now().plusHours(hours);
    }

    /**
     * Kiểm tra xem một thời điểm có trong quá khứ không.
     */
    public static boolean isPast(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * Định dạng số giây thành chuỗi dễ đọc: "2d 5h", "4h 30m", "45m 12s", "30s", "0s"
     */
    public static String formatDurationSeconds(Long totalSeconds) {
        if (totalSeconds == null || totalSeconds <= 0) {
            return "0s";
        }
        long days = totalSeconds / 86400;
        long remainAfterDays = totalSeconds % 86400;
        long hours = remainAfterDays / 3600;
        long remainAfterHours = remainAfterDays % 3600;
        long minutes = remainAfterHours / 60;
        long seconds = remainAfterHours % 60;

        if (days > 0) {
            return hours > 0 ? String.format("%dd %dh", days, hours) : String.format("%dd", days);
        }
        if (hours > 0) {
            return minutes > 0 ? String.format("%dh %dm", hours, minutes) : String.format("%dh", hours);
        }
        if (minutes > 0) {
            return seconds > 0 ? String.format("%dm %ds", minutes, seconds) : String.format("%dm", minutes);
        }
        return String.format("%ds", seconds);
    }

    /**
     * Định dạng số phút thành chuỗi dễ đọc: "2d 5h", "4h 30m", "45m", "0m"
     */
    public static String formatDurationMinutes(Long minutes) {
        if (minutes == null || minutes <= 0) {
            return "0m";
        }
        return formatDurationSeconds(minutes * 60);
    }
}
