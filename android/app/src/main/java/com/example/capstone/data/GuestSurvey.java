package com.example.capstone.data;

/**
 * 비회원의 설문 결과: 앱이 켜져 있는 동안만 메모리에 둔다 (기기에 저장하지 않는다).
 * 앱을 끄거나 로그인·로그아웃하면 사라지고, 다시 보려면 설문을 다시 한다.
 * 그동안 홈 체형 맞춤 추천과 내 체형 피드백이 이 값을 쓴다.
 */
public final class GuestSurvey {

    private static int[] answers;
    private static float heightCm;
    private static int type = -1;
    private static long savedAt;

    private GuestSurvey() {
    }

    public static synchronized void set(int[] surveyAnswers, float height, int resultType) {
        answers = surveyAnswers == null ? null : surveyAnswers.clone();
        heightCm = height;
        type = resultType;
        savedAt = System.currentTimeMillis();
    }

    public static synchronized void clear() {
        answers = null;
        heightCm = 0f;
        type = -1;
        savedAt = 0L;
    }

    public static synchronized boolean has() {
        return answers != null;
    }

    public static synchronized int[] answers() {
        return answers == null ? null : answers.clone();
    }

    public static synchronized float heightCm() {
        return heightCm;
    }

    /** 체형 코드 (진단 전이면 -1) */
    public static synchronized int type() {
        return type;
    }

    public static synchronized long savedAt() {
        return savedAt;
    }
}
