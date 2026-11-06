package com.example.capstone.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneInputTest {

    @Test
    public void hyphensFollowTheDigitsAsTheyAreTyped() {
        assertEquals("0", PhoneInput.format("0"));
        assertEquals("01", PhoneInput.format("01"));
        assertEquals("010", PhoneInput.format("010"));
        assertEquals("010-1", PhoneInput.format("0101"));
        assertEquals("010-1234", PhoneInput.format("0101234"));
        assertEquals("010-1234-5", PhoneInput.format("01012345"));
        assertEquals("010-1234-5678", PhoneInput.format("01012345678"));
    }

    @Test
    public void onlyDigitsAreKept() {
        assertEquals("01012345678", PhoneInput.digitsOf("010-1234 5678abc+*#"));
    }

    @Test
    public void onlyNumbersStartingWith010AreAllowed() {
        assertTrue(PhoneInput.isAllowed(""));
        assertTrue(PhoneInput.isAllowed("0"));
        assertTrue(PhoneInput.isAllowed("01"));
        assertTrue(PhoneInput.isAllowed("01012345678"));
        assertFalse(PhoneInput.isAllowed("1"));
        assertFalse(PhoneInput.isAllowed("02"));
        assertFalse(PhoneInput.isAllowed("011"));
        assertFalse(PhoneInput.isAllowed("010123456789"));
    }

    @Test
    public void nonDigitTypingIsBlockedBeforeItEntersTheField() {
        assertEquals("", PhoneInput.filterInsert("010", 3, 3, "a"));
        assertEquals("", PhoneInput.filterInsert("010", 3, 3, "-"));
        assertEquals("", PhoneInput.filterInsert("010", 3, 3, " "));
        assertEquals("1", PhoneInput.filterInsert("010", 3, 3, "1"));
    }

    @Test
    public void typingBreaking010OrTooLongIsBlocked() {
        assertEquals("", PhoneInput.filterInsert("", 0, 0, "1"));
        assertEquals("", PhoneInput.filterInsert("0", 1, 1, "2"));
        assertEquals("", PhoneInput.filterInsert("01", 2, 2, "1"));
        assertEquals("", PhoneInput.filterInsert("010-1234-5678", 13, 13, "9"));
    }

    @Test
    public void pasteKeepsDigitsAndCutsTheOverflow() {
        assertEquals("01012345678", PhoneInput.filterInsert("", 0, 0, "010-1234-5678"));
        assertEquals("01012345678", PhoneInput.filterInsert("", 0, 0, "01012345678999"));
        assertEquals("", PhoneInput.filterInsert("", 0, 0, "02-123-4567"));
    }

    @Test
    public void replacingSelectionCountsOnlyWhatRemains() {
        // 전체를 선택하고 다른 번호를 붙여 넣는 경우
        assertEquals("01099998888", PhoneInput.filterInsert("010-1234-5678", 0, 13, "010-9999-8888"));
    }
}
