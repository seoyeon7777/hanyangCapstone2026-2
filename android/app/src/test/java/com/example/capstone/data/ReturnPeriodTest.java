package com.example.capstone.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** 반품·교환은 주문일로부터 7일 이내에만 신청할 수 있다 */
public class ReturnPeriodTest {

    private static long at(String date) throws Exception {
        return new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(date).getTime();
    }

    @Test
    public void withinSevenDaysIsAllowed() throws Exception {
        assertFalse(MyPageStore.isReturnPeriodOver("2026.11.14", at("2026.11.14")));
        assertFalse(MyPageStore.isReturnPeriodOver("2026.11.14", at("2026.11.21")));   // 7일째까지
    }

    @Test
    public void afterSevenDaysIsOver() throws Exception {
        assertTrue(MyPageStore.isReturnPeriodOver("2026.11.14", at("2026.11.22")));
        assertTrue(MyPageStore.isReturnPeriodOver("2026.11.10", at("2026.11.21")));
    }

    @Test
    public void unreadableDateDoesNotBlock() throws Exception {
        assertFalse(MyPageStore.isReturnPeriodOver("", at("2026.11.21")));
        assertFalse(MyPageStore.isReturnPeriodOver("날짜 아님", at("2026.11.21")));
    }
}
