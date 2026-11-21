package com.example.capstone.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** 결제 후 3일이 지나면 배송완료(임시), 반품·교환은 배송완료 후 7일 이내에만 신청할 수 있다 */
public class ReturnPeriodTest {

    private static long at(String date) throws Exception {
        return new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(date).getTime();
    }

    @Test
    public void deliveredThreeDaysAfterOrder() throws Exception {
        assertFalse(MyPageStore.isDelivered("2026.11.18", at("2026.11.18")));
        assertFalse(MyPageStore.isDelivered("2026.11.18", at("2026.11.20")));
        assertTrue(MyPageStore.isDelivered("2026.11.18", at("2026.11.21")));
        assertFalse(MyPageStore.isDelivered("날짜 아님", at("2026.11.21")));
    }

    @Test
    public void returnIsAllowedForSevenDaysAfterDelivery() throws Exception {
        assertFalse(MyPageStore.isReturnPeriodOver("2026.11.11", at("2026.11.11")));
        assertFalse(MyPageStore.isReturnPeriodOver("2026.11.11", at("2026.11.21")));   // 배송완료(11/14) 후 7일째까지
    }

    @Test
    public void returnIsOverAfterSevenDaysFromDelivery() throws Exception {
        assertTrue(MyPageStore.isReturnPeriodOver("2026.11.11", at("2026.11.22")));
        assertTrue(MyPageStore.isReturnPeriodOver("2026.11.01", at("2026.11.21")));
    }

    @Test
    public void unreadableDateDoesNotBlock() throws Exception {
        assertFalse(MyPageStore.isReturnPeriodOver("", at("2026.11.21")));
        assertFalse(MyPageStore.isReturnPeriodOver("날짜 아님", at("2026.11.21")));
    }
}
