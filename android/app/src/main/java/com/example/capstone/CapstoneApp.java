package com.example.capstone;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 모든 화면에서 하단 내비게이션 바(뒤로·홈 버튼 줄)를 숨긴다.
 * 화면 아래쪽 버튼이 바에 가려지는 문제 해결. 아래에서 위로 쓸어올리면 잠깐 나타난다.
 * 다이얼로그·키보드가 닫히면 바가 다시 나올 수 있어서, 포커스를 되찾을 때마다 다시 숨긴다.
 */
public class CapstoneApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activity.getWindow().getDecorView()
                        .getViewTreeObserver()
                        .addOnWindowFocusChangeListener(hasFocus -> {
                            if (hasFocus) {
                                hideNavigationBar(activity.getWindow());
                            }
                        });
            }

            @Override
            public void onActivityResumed(Activity activity) {
                hideNavigationBar(activity.getWindow());
            }

            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private static void hideNavigationBar(Window window) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.navigationBars());
    }
}
