package com.example.capstone.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MaskTextTest {

    @Test
    public void holderKeepsFirstAndLast() {
        assertEquals("홍*동", MaskText.holder("홍길동"));
        assertEquals("남**수", MaskText.holder("남궁민수"));
    }

    @Test
    public void holderShortNames() {
        assertEquals("홍*", MaskText.holder("홍길"));
        assertEquals("홍", MaskText.holder("홍"));
        assertEquals("", MaskText.holder(null));
        assertEquals("홍*동", MaskText.holder("  홍길동 "));
    }

    @Test
    public void accountKeepsFirstFourAndLastFour() {
        assertEquals("1000**-**-**1001", MaskText.account("100000-01-001001"));
    }

    @Test
    public void accountWithoutHyphens() {
        assertEquals("1234**7890", MaskText.account("1234567890"));
    }

    @Test
    public void accountShort() {
        assertEquals("1***5", MaskText.account("12345"));
        assertEquals("", MaskText.account(null));
    }

    @Test
    public void userIdKeepsFirstTwoAndLastCharacters() {
        assertEquals("te*****2", MaskText.userId("tester02"));
        assertEquals("ab*d", MaskText.userId("abcd"));
        assertEquals("a**", MaskText.userId("abc"));
        assertEquals("", MaskText.userId(null));
    }
}
