package com.example.capstone.mypage;

import com.example.capstone.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * 마이페이지 하위 화면에서 반복되는 화면 처리 모음.
 */
final class MyPageUi {

    /** 활성 탭 글자색 */
    private static final int COLOR_TAB_ACTIVE = Color.BLACK;
    /** 비활성 탭 글자색 (MyPageTab 스타일 기본값과 동일) */
    private static final int COLOR_TAB_INACTIVE = Color.parseColor("#AAAAAA");

    private MyPageUi() {
    }

    /** view_mypage_toolbar 의 제목을 넣고 뒤로가기를 연결한다. */
    static void setupToolbar(Activity activity, String title) {
        TextView tvTitle = activity.findViewById(R.id.tvToolbarTitle);
        tvTitle.setText(title);
        activity.findViewById(R.id.btnBack).setOnClickListener(v -> activity.finish());
    }

    /** index 번째 탭만 굵게 + 밑줄을 보여준다. */
    static void selectTab(TextView[] tabs, View[] lines, int index) {
        for (int i = 0; i < tabs.length; i++) {
            boolean active = i == index;
            tabs[i].setTextColor(active ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
            tabs[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            lines[i].setVisibility(active ? View.VISIBLE : View.GONE);
        }
    }

    /** index 번째 칩만 선택 상태(검정 배경)로 만든다. */
    static void selectChip(TextView[] chips, int index) {
        for (int i = 0; i < chips.length; i++) {
            chips[i].setSelected(i == index);
        }
    }

    /** "확인/취소" 확인창. 확인을 누르면 onConfirm 실행 */
    static void confirm(Context context, String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인", (d, w) -> onConfirm.run())
                .setNegativeButton("취소", null)
                .show();
    }

    /** 010-1234-5678 형태로 맞춘다. 숫자 자릿수가 맞지 않으면 null */
    static String normalizePhone(String input) {
        String digits = input.replaceAll("[^0-9]", "");
        if (!digits.matches("01[016789][0-9]{7,8}")) {
            return null;
        }
        int midEnd = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, midEnd) + "-" + digits.substring(midEnd);
    }
}
