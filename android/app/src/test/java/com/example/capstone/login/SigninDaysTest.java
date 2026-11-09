package com.example.capstone.login;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public class SigninDaysTest {

    @Test
    public void onlyRealDaysAreListed() {
        assertEquals(31, SigninActivity.daysInMonth(2001, 1));
        assertEquals(28, SigninActivity.daysInMonth(2001, 2));
        assertEquals(29, SigninActivity.daysInMonth(2000, 2));
        assertEquals(28, SigninActivity.daysInMonth(1900, 2));
        assertEquals(30, SigninActivity.daysInMonth(2001, 4));
        assertEquals(31, SigninActivity.daysInMonth(2001, 12));
    }

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c;
    }

    @Test
    public void futureDatesAreNotListed() {
        Calendar today = day(2026, 11, 8);
        assertEquals(11, SigninActivity.maxMonth(2026, today));
        assertEquals(12, SigninActivity.maxMonth(2025, today));
        assertEquals(12, SigninActivity.maxMonth(0, today));
        assertEquals(8, SigninActivity.maxDay(2026, 11, today));
        assertEquals(31, SigninActivity.maxDay(2026, 10, today));
        assertEquals(30, SigninActivity.maxDay(2025, 11, today));
        assertEquals(31, SigninActivity.maxDay(2026, 0, today));
        // 하루 지나면 그날까지
        assertEquals(9, SigninActivity.maxDay(2026, 11, day(2026, 11, 9)));
        assertEquals(12, SigninActivity.maxMonth(2026, day(2026, 12, 1)));
        assertEquals(1, SigninActivity.maxDay(2026, 12, day(2026, 12, 1)));
    }
}
