package com.example.capstone.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
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
                activity.findViewById(R.id.menuCart),
                activity.findViewById(R.id.menuMy)
        };

        int[] tabTypes = {
                TAB_HOME,
                TAB_CATEGORY,
                TAB_WISH,
                TAB_FITTING,
                TAB_CART,
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

    private static void moveTo(Activity activity, int tab) {

        if (tab == TAB_MY) {

            Class<?> target =
                    LoginSession.isLoggedIn(activity)
                            ? MyPageActivity.class
                            : LoginMainActivity.class;

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
                target = FittingActivity.class;
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
