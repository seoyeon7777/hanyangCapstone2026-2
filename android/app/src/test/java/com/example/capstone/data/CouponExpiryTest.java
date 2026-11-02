package com.example.capstone.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class CouponExpiryTest {

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    @Test
    public void passedDateIsExpired() {
        assertTrue(CouponExpiry.isExpired("2026.08.15 23:59 까지", at(2026, 11, 1, 12, 0)));
    }

    @Test
    public void untilEndTimeIsNotExpired() {
        String text = "2026.08.15 23:59 까지";
        assertFalse(CouponExpiry.isExpired(text, at(2026, 8, 15, 23, 59)));
        assertTrue(CouponExpiry.isExpired(text, at(2026, 8, 16, 0, 0)));
    }

    @Test
    public void unreadableDateIsNotExpired() {
        assertFalse(CouponExpiry.isExpired("제한 없음", at(2026, 11, 1, 12, 0)));
        assertFalse(CouponExpiry.isExpired(null, at(2026, 11, 1, 12, 0)));
    }
}
