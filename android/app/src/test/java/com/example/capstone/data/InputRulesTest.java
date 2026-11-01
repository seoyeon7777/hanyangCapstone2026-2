package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InputRulesTest {

    @Test
    public void emailIsTrimmedAndLowercased() {
        assertEquals("abc@gmail.com", InputRules.normalizeEmail("  Abc@Gmail.COM "));
        assertEquals("a.b_c@naver.com", InputRules.normalizeEmail("A.B_c@naver.com"));
        assertNull(InputRules.normalizeEmail("abc@"));
        assertNull(InputRules.normalizeEmail("abc@@gmail.com"));
        assertNull(InputRules.normalizeEmail("abcgmail.com"));
        assertNull(InputRules.normalizeEmail(null));
    }

    @Test
    public void phoneFormatsAreUnified() {
        assertEquals("010-1234-5678", InputRules.normalizePhone("01012345678"));
        assertEquals("010-1234-5678", InputRules.normalizePhone("010-1234-5678"));
        assertEquals("010-1234-5678", InputRules.normalizePhone("010 1234 5678"));
        assertEquals("011-123-4567", InputRules.normalizePhone("0111234567"));
        assertNull(InputRules.normalizePhone("02-123-4567"));
        assertNull(InputRules.normalizePhone("0101234"));
    }

    @Test
    public void passwordRuleIsOneRule() {
        assertTrue(InputRules.isValidPassword("abcd123!"));
        assertTrue(InputRules.isValidPassword("Abcdefgh1234567890!@"));
        assertFalse(InputRules.isValidPassword("abcd12!"));
        assertFalse(InputRules.isValidPassword("Abcdefgh1234567890!@x"));
        assertFalse(InputRules.isValidPassword("abcdefgh"));
        assertFalse(InputRules.isValidPassword("abcd1234"));
        assertFalse(InputRules.isValidPassword("abcd 123!"));
        assertFalse(InputRules.isValidPassword(null));
    }

    @Test
    public void birthDateMustExist() {
        assertTrue(InputRules.isValidBirth(2000, 2, 29));
        assertFalse(InputRules.isValidBirth(2001, 2, 29));
        assertFalse(InputRules.isValidBirth(2000, 4, 31));
        assertFalse(InputRules.isValidBirth(1000, 1, 1));
        assertFalse(InputRules.isValidBirth(3000, 1, 1));
        assertFalse(InputRules.isValidBirthYear(5000));
        assertEquals(29, InputRules.daysInMonth(2024, 2));
        assertEquals(28, InputRules.daysInMonth(1900, 2));
        assertEquals(30, InputRules.daysInMonth(2024, 11));
    }

    @Test
    public void nicknameAndGender() {
        assertEquals("하늘", InputRules.normalizeNickname(" 하늘 "));
        assertNull(InputRules.normalizeNickname("하"));
        assertNull(InputRules.normalizeNickname("하 늘"));
        assertNull(InputRules.normalizeNickname("12345678901"));
        assertEquals("F", InputRules.toDbGender("여성"));
        assertEquals("F", InputRules.toDbGender(""));
        assertEquals("M", InputRules.toDbGender("남성"));
        assertEquals("기타", InputRules.toDbGender("other"));
    }

    @Test
    public void emailIsMasked() {
        assertEquals("ab**@gmail.com", InputRules.maskEmail("abcd@gmail.com"));
        assertEquals("a**@gmail.com", InputRules.maskEmail("a@gmail.com"));
    }

    @Test
    public void couponDiscountFollowsRules() {
        Coupons.Coupon percent = Coupons.find("FALL_LONGSLEEVE_30");
        assertEquals(30000, percent.discountFor(200000));
        assertEquals(3000, percent.discountFor(10000));

        Coupons.Coupon minOrder = Coupons.find("ALL_5");
        assertEquals(0, minOrder.discountFor(19999));
        assertEquals(1000, minOrder.discountFor(20000));

        Coupons.Coupon amount = Coupons.findByCode("welcome5000");
        assertEquals(5000, amount.discountFor(10000));
        assertEquals(0, amount.discountFor(9000));
    }

    @Test
    public void shippingIsFreeOverMinimum() {
        assertEquals(ShopStore.SHIPPING_FEE, ShopStore.shippingFee(10000));
        assertEquals(0, ShopStore.shippingFee(ShopStore.FREE_SHIPPING_MIN));
        assertEquals(0, ShopStore.shippingFee(0));
    }
}
