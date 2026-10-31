package com.example.capstone.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecommendTimingTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;

    // 2번: 재진단 안내는 한 번 보여 주고, 쉬는 기간(3일) 동안은 다시 안 띄운다
    @Test
    public void rediagNoticeShownOnceThenRests() {
        assertTrue(RecommendTiming.shouldShowRediagNotice(0L, 0L, NOW));                  // 처음
        assertFalse(RecommendTiming.shouldShowRediagNotice(NOW, 0L, NOW + 1000));         // 바로 다음 클릭
        assertFalse(RecommendTiming.shouldShowRediagNotice(NOW, 0L, NOW + 2 * DAY));      // 2일 뒤
        assertTrue(RecommendTiming.shouldShowRediagNotice(NOW, 0L, NOW + 3 * DAY));       // 3일 뒤
    }

    @Test
    public void rediagNoticeAllowedAgainAfterNewSurvey() {
        // 안내 뒤에 설문을 다시 했으면 쉬는 기간 안이어도 다시 띄울 수 있다
        assertTrue(RecommendTiming.shouldShowRediagNotice(NOW, NOW + 1000, NOW + 2000));
        assertFalse(RecommendTiming.shouldShowRediagNotice(NOW, NOW - 1000, NOW + 2000));
    }

    // 5번: 연결에 실패하면 30초 동안은 클릭·찜 기록을 보내지 않는다
    @Test
    public void eventsPausedShortlyAfterConnectionFailure() {
        assertFalse(RecommendTiming.isOffline(0L, NOW));                                  // 실패한 적 없음
        assertTrue(RecommendTiming.isOffline(NOW, NOW + 1000));
        assertTrue(RecommendTiming.isOffline(NOW, NOW + RecommendTiming.OFFLINE_PAUSE_MS - 1));
        assertFalse(RecommendTiming.isOffline(NOW, NOW + RecommendTiming.OFFLINE_PAUSE_MS));
    }
}
