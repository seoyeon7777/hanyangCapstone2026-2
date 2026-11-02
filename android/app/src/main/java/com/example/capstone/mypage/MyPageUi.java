package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.ui.AppDialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    static void fillChoiceGrid(LinearLayout container, String[] items, int columns,
                               String selected, java.util.function.Consumer<String> onPick) {
        Context context = container.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int gap = Math.round(8 * density);

        container.removeAllViews();
        java.util.List<TextView> chips = new java.util.ArrayList<>();
        LinearLayout row = null;

        for (int i = 0; i < items.length; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.round(44 * density));
                rowLp.topMargin = i == 0 ? 0 : gap;
                container.addView(row, rowLp);
            }

            TextView chip = new TextView(context);
            chip.setText(items[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTextColor(Color.parseColor("#111111"));
            chip.setBackgroundResource(R.drawable.bg_option_chip);
            chip.setSelected(items[i].equals(selected));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i % columns != columns - 1) {
                lp.rightMargin = gap;
            }
            row.addView(chip, lp);
            chips.add(chip);

            String value = items[i];
            chip.setOnClickListener(v -> {
                for (TextView c : chips) {
                    c.setSelected(c == chip);
                    c.setTypeface(null, c == chip ? Typeface.BOLD : Typeface.NORMAL);
                }
                onPick.accept(value);
            });
        }

        int rest = items.length % columns;
        if (rest != 0 && row != null) {
            for (int i = rest; i < columns; i++) {
                View empty = new View(context);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (i != columns - 1) {
                    lp.rightMargin = gap;
                }
                row.addView(empty, lp);
            }
        }
    }

    /** "확인/취소" 확인창. 확인을 누르면 onConfirm 실행 */
    static void confirm(Context context, String title, String message, Runnable onConfirm) {
        AppDialog.show(context, true, title, message,
                "취소", null,
                "확인", onConfirm);
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
