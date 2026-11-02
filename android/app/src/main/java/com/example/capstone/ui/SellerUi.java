package com.example.capstone.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 판매자 화면(가입·센터·상품 등록)이 같이 쓰는 화면 조각. 레이아웃 XML 없이 코드로 만든다. */
final class SellerUi {

    static final int BLACK = Color.rgb(17, 17, 17);
    static final int GRAY = Color.rgb(120, 120, 120);
    static final int LINE = Color.rgb(224, 224, 224);
    static final int RED = Color.rgb(200, 40, 40);

    private SellerUi() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 세로로 쌓는 스크롤 화면의 본문 줄. 화면 전체를 이 ScrollView에 담는다. */
    static LinearLayout page(Context context, ScrollView scroll) {
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 40));
        scroll.addView(column, new ViewGroup.LayoutParams(-1, -2));
        return column;
    }

    static TextView title(Context context, String text) {
        TextView view = text(context, text, 22, BLACK, true);
        view.setPadding(0, dp(context, 8), 0, dp(context, 8));
        return view;
    }

    static TextView section(Context context, String text) {
        TextView view = text(context, text, 16, BLACK, true);
        view.setPadding(0, dp(context, 26), 0, dp(context, 6));
        return view;
    }

    static TextView label(Context context, String text) {
        TextView view = text(context, text, 13, GRAY, false);
        view.setPadding(0, dp(context, 10), 0, dp(context, 2));
        return view;
    }

    static TextView text(Context context, String text, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    static EditText edit(Context context, String hint, int inputType) {
        EditText view = new EditText(context);
        view.setHint(hint);
        view.setInputType(inputType);
        view.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        view.setTextSize(15);
        view.setTextColor(BLACK);
        view.setHintTextColor(Color.rgb(170, 170, 170));
        view.setBackground(box(context, Color.WHITE, LINE));
        view.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        return view;
    }

    static Button button(Context context, String text) {
        Button view = new Button(context);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(16);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(box(context, BLACK, BLACK));
        return view;
    }

    static Button outlineButton(Context context, String text) {
        Button view = new Button(context);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTextColor(BLACK);
        view.setBackground(box(context, Color.WHITE, BLACK));
        return view;
    }

    static CheckBox check(Context context, String text) {
        CheckBox view = new CheckBox(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(BLACK);
        return view;
    }

    static GradientDrawable box(Context context, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(context, 1), stroke);
        drawable.setCornerRadius(dp(context, 6));
        return drawable;
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static LinearLayout.LayoutParams matchWidth(Context context, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    static LinearLayout.LayoutParams weighted(Context context, int marginEndDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMarginEnd(dp(context, marginEndDp));
        return params;
    }

    static void setEnabled(android.view.View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.5f);
    }
}
