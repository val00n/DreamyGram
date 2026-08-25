package com.val00n.dmpg.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("HH:mm");
                }
            };

    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("MM.dd HH:mm");
                }
            };

    private static final ThreadLocal<SimpleDateFormat> DAY_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyyMMdd");
                }
            };

    private static final ThreadLocal<SimpleDateFormat> DATE_SEPARATOR_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("d MMMM yyyy");
                }
            };

    public static String formatTime(long seconds) {
        return TIME_FORMAT.get().format(new Date(seconds * 1000));
    }

    public static String formatDateTime(long seconds) {
        return DATE_TIME_FORMAT.get().format(new Date(seconds * 1000));
    }

    public static boolean isToday(long seconds, long nowMillis) {
        if (seconds <= 0) return false;
        return dayKey(seconds).equals(dayKey(nowMillis / 1000));
    }

    public static String dayKey(long seconds) {
        if (seconds <= 0) return "";
        return DAY_FORMAT.get().format(new Date(seconds * 1000));
    }

    public static String formatDateSeparator(long seconds) {
        if (seconds <= 0) return "";
        return DATE_SEPARATOR_FORMAT.get().format(new Date(seconds * 1000));
    }

    public static String formatChatTime(long seconds, boolean showDate, long nowMillis) {
        if (seconds <= 0) return "XX:XX";

        if (!showDate || isToday(seconds, nowMillis)) {
            return formatTime(seconds);
        }

        return formatDateTime(seconds);
    }
}