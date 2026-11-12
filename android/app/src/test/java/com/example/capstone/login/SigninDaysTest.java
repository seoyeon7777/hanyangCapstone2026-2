package com.example.capstone.login;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
}
