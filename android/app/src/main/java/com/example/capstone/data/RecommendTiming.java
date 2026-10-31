package com.example.capstone.data;

/**
 * RecommendApi의 시간 판단만 모은 곳 (안드로이드 기능을 안 써서 PC 단위 테스트로 확인할 수 있다).
 */
public final class RecommendTiming {

    // 서버에 연결하지 못하면 이 시간 동안은 클릭·찜 기록을 보내지 않는다 (홈 불러오기는 계속 시도)
    public static final long OFFLINE_PAUSE_MS = 30_000L;

    // 재진단 안내는 한 번 보여 주면 이 기간 동안 다시 띄우지 않는다
    public static final long REDIAG_NOTICE_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000;

    private RecommendTiming() {
    }

    /**
     * 재진단 안내를 띄울지. 처음이거나, 마지막 안내 뒤 쉬는 기간이 지났거나,
     * 마지막 안내 뒤에 설문을 다시 했으면 true.
     */
    public static boolean shouldShowRediagNotice(long lastShownAt, long lastSurveyAt, long now) {
        return lastShownAt <= 0L
                || now - lastShownAt >= REDIAG_NOTICE_COOLDOWN_MS
                || lastSurveyAt > lastShownAt;
    }

    /** 마지막 연결 실패 뒤 쉬는 시간 안이면 true (기록을 보내지 않는다) */
    public static boolean isOffline(long lastFailAt, long now) {
        return lastFailAt > 0L && now - lastFailAt < OFFLINE_PAUSE_MS;
    }
}
