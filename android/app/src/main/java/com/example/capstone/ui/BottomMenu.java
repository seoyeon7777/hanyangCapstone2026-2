package com.example.capstone.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.login.LoginGate;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.login.MyPageActivity;

public final class BottomMenu {

    public static final int TAB_HOME = 0;
    public static final int TAB_WISH = 1;
    public static final int TAB_FITTING = 2;
    public static final int TAB_CART = 3;
    public static final int TAB_MY = 4;
    public static final int TAB_CATEGORY = 5;

    private static final int COLOR_ACTIVE = Color.BLACK;
    private static final int COLOR_INACTIVE =
            Color.parseColor("#888888");

    private BottomMenu() {
    }

    public interface LeaveGuard {
        void confirmLeave(Runnable leave);
    }

    public static void setup(Activity activity, int currentTab) {
        setup(activity, currentTab, null);
    }

    public static void setup(Activity activity, int currentTab, LeaveGuard guard) {

        TextView[] tabs = {
                activity.findViewById(R.id.menuHome),
                activity.findViewById(R.id.menuCategory),
                activity.findViewById(R.id.menuWish),
                activity.findViewById(R.id.menuFitting),
                activity.findViewById(R.id.menuMy)
        };

        int[] tabTypes = {
                TAB_HOME,
                TAB_CATEGORY,
                TAB_WISH,
                TAB_FITTING,
                TAB_MY
        };

        for (int i = 0; i < tabs.length; i++) {

            TextView view = tabs[i];

            if (view == null) {
                continue;
            }

            int tab = tabTypes[i];
            boolean active = tab == currentTab;

            view.setTextColor(
                    active ? COLOR_ACTIVE : COLOR_INACTIVE
            );

            view.setCompoundDrawableTintList(
                    ColorStateList.valueOf(
                            active ? COLOR_ACTIVE : COLOR_INACTIVE
                    )
            );

            view.setTypeface(
                    null,
                    active ? Typeface.BOLD : Typeface.NORMAL
            );

            view.setTextSize(10);

            view.setOnClickListener(v -> {
                if (guard == null) {
                    moveTo(activity, tab);
                } else {
                    guard.confirmLeave(() -> moveTo(activity, tab));
                }
            });
        }
    }

    /** 로그인했고 저장한 체형이 하나라도 있으면 true */
    static boolean hasSavedBody(Activity activity) {
        return LoginSession.isLoggedIn(activity)
                && !com.example.capstone.data.BodyProfileStore.getProfiles(activity).isEmpty();
    }

    public static void openCart(Activity activity) {
        // 장바구니 보기는 로그인해야 한다 (비회원이면 로그인 안내 → 로그인 뒤 장바구니로)
        LoginGate.require(activity, () -> activity.startActivity(
                new Intent(activity, CartActivity.class)
        ));
    }

    private static void moveTo(Activity activity, int tab) {

        // 찜·장바구니 탭은 로그인해야 열 수 있다 (비회원이면 로그인 안내 → 로그인 뒤 그 탭으로)
        if ((tab == TAB_WISH || tab == TAB_CART) && !LoginSession.isLoggedIn(activity)) {
            LoginGate.require(activity, () -> moveTo(activity, tab));
            return;
        }

        if (tab == TAB_MY) {

            Class<?> target =
                    LoginSession.isLoggedIn(activity)
                            ? MyPageActivity.class
                            : LoginMainActivity.class;

            if (activity.getClass() == target) {
                return;
            }

            activity.startActivity(
                    new Intent(activity, target)
            );
            return;
        }

        Class<?> target;

        switch (tab) {

            case TAB_CATEGORY:
                target = CategoryMenuActivity.class;
                break;

            case TAB_WISH:
                target = WishActivity.class;
                break;

            case TAB_FITTING:
                // 체형분석 탭: 저장한 체형이 있으면 체형 목록(예전 마이페이지 '내 체형관리'),
                // 없으면(처음 가입한 사람·비로그인) 바로 새 분석 화면
                if (hasSavedBody(activity)) {
                    target = com.example.capstone.mypage.MyBodyActivity.class;
                } else {
                    if (activity.getClass() == FittingActivity.class) {
                        return;
                    }
                    Intent first = new Intent(activity, FittingActivity.class)
                            .putExtra(FittingActivity.EXTRA_AS_TAB, true)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(first);
                    if (!(activity instanceof MainActivity)) {
                        activity.finish();
                    }
                    return;
                }
                break;

            case TAB_CART:
                target = CartActivity.class;
                break;

            case TAB_HOME:
            default:
                target = MainActivity.class;
                break;
        }

        if (activity.getClass() == target) {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).showHomeTab();
            }
            return;
        }

        Intent intent = new Intent(activity, target);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        activity.startActivity(intent);

        if (!(activity instanceof MainActivity)) {
            activity.finish();
        }
    }
}
