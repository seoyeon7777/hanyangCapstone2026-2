package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SellerFormRulesTest {

    @Test
    public void price_accepts_commas_and_won_sign() {
        assertEquals(39000, SellerFormRules.parsePrice("39,000"));
        assertEquals(39000, SellerFormRules.parsePrice(" 39000원 "));
        assertEquals(1, SellerFormRules.parsePrice("1"));
        assertEquals(10_000_000, SellerFormRules.parsePrice("10000000"));
    }

    @Test
    public void price_rejects_bad_input() {
        assertEquals(-1, SellerFormRules.parsePrice(""));
        assertEquals(-1, SellerFormRules.parsePrice("0"));
        assertEquals(-1, SellerFormRules.parsePrice("-5"));
        assertEquals(-1, SellerFormRules.parsePrice("12.5"));
        assertEquals(-1, SellerFormRules.parsePrice("10000001"));
        assertEquals(-1, SellerFormRules.parsePrice("99999999999999"));   // 정수 범위를 넘어도 죽지 않는다
        assertEquals(-1, SellerFormRules.parsePrice("abc"));
        assertEquals(-1, SellerFormRules.parsePrice(null));
    }

    @Test
    public void cm_accepts_decimals_with_dot_or_comma() {
        assertEquals(46.5, SellerFormRules.parseCm("46.5"), 1e-9);
        assertEquals(46.5, SellerFormRules.parseCm("46,5"), 1e-9);
        assertEquals(120.0, SellerFormRules.parseCm("120"), 1e-9);
    }

    @Test
    public void cm_rejects_bad_input() {
        assertTrue(Double.isNaN(SellerFormRules.parseCm("")));
        assertTrue(Double.isNaN(SellerFormRules.parseCm(".")));
        assertTrue(Double.isNaN(SellerFormRules.parseCm("4.6.5")));
        assertTrue(Double.isNaN(SellerFormRules.parseCm("1e3")));
        assertTrue(Double.isNaN(SellerFormRules.parseCm("1234")));
        assertTrue(Double.isNaN(SellerFormRules.parseCm(null)));
    }

    @Test
    public void sizes_are_sorted_like_the_server() {
        assertEquals(Arrays.asList("S", "M", "L", "FREE"),
                SellerFormRules.sortSizes(Arrays.asList("FREE", "L", "S", "M")));
    }

    @Test
    public void composition_must_sum_to_100() {
        assertTrue(SellerFormRules.compositionSumsTo100(Arrays.asList(60.0, 40.0, null)));
        assertTrue(SellerFormRules.compositionSumsTo100(Arrays.asList(100.0)));
        assertFalse(SellerFormRules.compositionSumsTo100(Arrays.asList(60.0, 30.0)));
        assertFalse(SellerFormRules.compositionSumsTo100(Arrays.<Double>asList()));
    }

    @Test
    public void color_is_trimmed_and_uppercased() {
        assertEquals("SKY BLUE", SellerFormRules.normalizeColor("  sky   blue "));
        assertNull(SellerFormRules.normalizeColor("   "));
        assertNull(SellerFormRules.normalizeColor(new String(new char[31]).replace('\0', 'a')));
    }

    @Test
    public void brand_name_rules() {
        assertEquals("내 브랜드", SellerStore.normalizeBrand("  내   브랜드 "));
        assertNull(SellerStore.normalizeBrand("a"));
        assertNull(SellerStore.normalizeBrand(null));
        assertNull(SellerStore.normalizeBrand(new String(new char[31]).replace('\0', '가')));
    }
}
