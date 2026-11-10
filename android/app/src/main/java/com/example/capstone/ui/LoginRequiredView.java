package com.example.capstone.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.capstone.R;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;

public final class LoginRequiredView {

    private LoginRequiredView() {
    }

    public static boolean update(Activity activity, int iconRes, String message, View content) {

        boolean loggedIn = LoginSession.isLoggedIn(activity);

        View loginView = activity.findViewById(R.id.layoutLoginRequired);
        loginView.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        content.setVisibility(loggedIn ? View.VISIBLE : View.GONE);

        if (!loggedIn) {
            ImageView icon = activity.findViewById(R.id.imgLoginRequired);
            icon.setImageResource(iconRes);

            TextView text = activity.findViewById(R.id.textLoginRequired);
            text.setText(message);

            activity.findViewById(R.id.btnLoginRequired).setOnClickListener(v ->
                    activity.startActivity(new Intent(activity, LoginMainActivity.class)));
        }

        return loggedIn;
    }

    /** 로그인 안내 왼쪽 위에 이전 화면(‹) 버튼을 보여 준다. 누르면 이 화면을 닫는다 */
    public static void showBack(Activity activity) {
        View back = activity.findViewById(R.id.btnLoginRequiredBack);
        back.setVisibility(View.VISIBLE);
        back.setOnClickListener(v -> activity.finish());
    }
}
