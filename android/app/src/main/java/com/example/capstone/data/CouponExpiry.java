package com.example.capstone.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 쿠폰의 사용 기한 글자("2026.08.15 23:59 까지")로 기간이 지났는지 판단한다 */
public final class CouponExpiry {

    private static final String DATE_FORMAT = "yyyy.MM.dd HH:mm";

    private CouponExpiry() {
    }

    /** 날짜를 읽을 수 없으면 지난 것으로 보지 않는다 */
    public static boolean isExpired(String expireText, long nowMillis) {
        if (expireText == null || expireText.length() < DATE_FORMAT.length()) {
            return false;
        }
        try {
            Date end = new SimpleDateFormat(DATE_FORMAT, Locale.ROOT)
                    .parse(expireText.substring(0, DATE_FORMAT.length()));
            return end != null && end.getTime() < nowMillis;
        } catch (ParseException e) {
            return false;
        }
    }
}
