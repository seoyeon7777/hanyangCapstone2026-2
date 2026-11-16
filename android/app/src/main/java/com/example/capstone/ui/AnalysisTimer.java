package com.example.capstone.ui;

import android.os.SystemClock;
import android.util.Log;

/**
 * 사진 분석에 걸린 시간을 단계별로 logcat(태그 AnalysisTime)에 남긴다. 사진·치수 같은 개인 정보는 담지 않고 시간만 적는다.
 * 분석 시작 버튼을 누른 때부터 잰다 (start → mark → mark …).
 */
final class AnalysisTimer {

    private static final String TAG = "AnalysisTime";
    private static long startMs;
    private static long lastMs;

    private AnalysisTimer() {
    }

    static synchronized void start() {
        startMs = SystemClock.elapsedRealtime();
        lastMs = startMs;
        Log.i(TAG, "시작");
    }

    static synchronized void mark(String step) {
        long now = SystemClock.elapsedRealtime();
        Log.i(TAG, step + " +" + (now - lastMs) + "ms (누적 " + (now - startMs) + "ms)");
        lastMs = now;
    }
}
