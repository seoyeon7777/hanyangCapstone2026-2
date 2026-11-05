package com.example.capstone.login;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.widget.Toast;

import com.example.capstone.ui.AppDialog;

import java.lang.ref.WeakReference;

/**
 * 로그인해야 쓸 수 있는 기능(찜, 장바구니 담기, 찜·장바구니 보기, 바로 구매)의 관문.
 *
 * - 로그인돼 있으면 하려던 동작을 바로 실행한다.
 * - 비회원이면 "로그인이 필요해요" 창을 띄우고, "로그인하러 가기"를 누르면 로그인 화면으로 보낸다.
 *   로그인(또는 회원가입)을 마치면 로그인 화면들을 닫고 원래 화면으로 돌아와 하려던 동작을 이어서 한다.
 *   로그인하지 않고 돌아오면 하려던 동작은 취소된다.
 *
 * 돌아오는 일은 CapstoneApp의 화면 생명주기에서 onActivityResumed 로 알려 준다.
 */
public final class LoginGate {

    private static Runnable pending;
    private static WeakReference<Activity> owner;

    private LoginGate() {
    }

    /** 로그인한 상태에서만 action을 실행한다. 아니면 로그인 안내 창을 띄운다. */
    public static void require(Context context, Runnable action) {
        if (LoginSession.isLoggedIn(context)) {
            action.run();
            return;
        }

        Activity activity = findActivity(context);
        if (activity == null) {
            Toast.makeText(context, "로그인이 필요해요.", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDialog.show(activity, false,
                "로그인이 필요해요",
                "로그인하거나 회원가입을 하면 이용할 수 있어요.",
                "취소", null,
                "로그인하러 가기", () -> {
                    pending = action;
                    owner = new WeakReference<>(activity);
                    activity.startActivity(new Intent(activity, LoginMainActivity.class));
                });
    }

    /** 로그인 화면에서 돌아와 이어서 할 동작이 있는가 (있으면 로그인 뒤 마이페이지로 가지 않는다) */
    static boolean hasPending() {
        return pending != null;
    }

    /** CapstoneApp이 모든 화면의 onResume 때 부른다 */
    public static void onActivityResumed(Activity activity) {
        if (pending == null) {
            return;
        }

        if (isLoginFlow(activity)) {
            // 로그인(가입)을 마친 뒤 남은 로그인 화면은 닫고 원래 화면으로 돌아간다
            if (LoginSession.isLoggedIn(activity)) {
                activity.finish();
            }
            return;
        }

        Activity original = owner == null ? null : owner.get();
        Runnable action = pending;
        pending = null;
        owner = null;

        // 원래 화면으로 돌아왔고 로그인이 됐을 때만 하려던 동작을 이어서 한다
        if (activity == original
                && !activity.isFinishing()
                && LoginSession.isLoggedIn(activity)) {
            activity.getWindow().getDecorView().post(action);
        }
    }

    /** 로그인·회원가입 화면인가 (로그인한 뒤에도 쓰는 마이페이지는 아니다) */
    private static boolean isLoginFlow(Activity activity) {
        return activity.getClass().getName().startsWith("com.example.capstone.login.")
                && !(activity instanceof MyPageActivity)
                && !(activity instanceof CouponActivity)
                && !(activity instanceof InquiryActivity);
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
