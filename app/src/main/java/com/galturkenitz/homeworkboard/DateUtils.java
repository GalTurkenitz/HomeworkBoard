package com.galturkenitz.homeworkboard;

import java.util.Calendar;

// פעולות עזר לתאריכים. תאריך נשמר כמחרוזת בפורמט dd/MM/yyyy
public class DateUtils {

    public static String format(int day, int month, int year) {
        // month מגיע מה-DatePicker מ-0 (ינואר) עד 11
        return String.format("%02d/%02d/%04d", day, month + 1, year);
    }

    // הופך dd/MM/yyyy ל-yyyyMMdd כדי שמיון של מחרוזות ימיין לפי תאריך
    public static String toSortKey(String date) {
        if (date == null || date.length() != 10) {
            return "99999999";
        }
        return date.substring(6, 10) + date.substring(3, 5) + date.substring(0, 2);
    }

    public static String today() {
        Calendar c = Calendar.getInstance();
        return format(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH), c.get(Calendar.YEAR));
    }

    // האם תאריך ההגשה כבר עבר
    public static boolean isPast(String date) {
        return toSortKey(date).compareTo(toSortKey(today())) < 0;
    }
}
