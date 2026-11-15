package com.example.capstone.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.capstone.R;

public final class AppDialog {

    public interface InputHandler {
        boolean onInput(String text);
    }

    public interface PickHandler {
        void onPick(int index);
    }

    private AppDialog() {
    }

    public static void show(Context context, boolean warning, String title, String message,
                            String lightText, Runnable onLight,
                            String darkText, Runnable onDark) {
        View view = inflate(context, warning, title, message);

        AlertDialog dialog = create(context, view);
        bindButton(view.findViewById(R.id.btnDialogLight), lightText, dialog, onLight);
        bindButton(view.findViewById(R.id.btnDialogDark), darkText, dialog, onDark);
        dialog.show();
    }

    public static void alert(Context context, String title, String message, String buttonText) {
        showOneButton(context, inflate(context, false, title, message), buttonText);
    }

    /** 제목 없이 안내 문장(굵지 않게) + 버튼 하나 */
    public static void notice(Context context, String message, String buttonText) {
        View view = inflate(context, false, message, null);
        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT);
        tvTitle.setTextSize(16);
        tvTitle.setLineSpacing(0, 1.3f);
        showOneButton(context, view, buttonText);
    }

    private static void showOneButton(Context context, View view, String buttonText) {
        view.findViewById(R.id.btnDialogLight).setVisibility(View.GONE);

        TextView btnDark = view.findViewById(R.id.btnDialogDark);
        ((android.widget.LinearLayout.LayoutParams) btnDark.getLayoutParams()).setMarginStart(0);

        AlertDialog dialog = create(context, view);
        bindButton(btnDark, buttonText, dialog, null);
        dialog.show();
    }

    public static void input(Context context, String title, String message,
                             String initialText, String hint, int inputType,
                             String okText, InputHandler onOk) {
        View view = inflate(context, false, title, message);

        EditText etInput = view.findViewById(R.id.etDialogInput);
        etInput.setVisibility(View.VISIBLE);
        etInput.setInputType(inputType);
        etInput.setHint(hint);
        if (initialText != null) {
            etInput.setText(initialText);
            etInput.setSelection(etInput.length());
        }

        AlertDialog dialog = create(context, view);
        bindButton(view.findViewById(R.id.btnDialogLight), "취소", dialog, null);

        TextView btnOk = view.findViewById(R.id.btnDialogDark);
        btnOk.setText(okText);
        btnOk.setOnClickListener(v -> {
            if (onOk.onInput(etInput.getText().toString().trim())) {
                dialog.dismiss();
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        etInput.requestFocus();
        dialog.show();
    }

    public static void choose(Context context, String title, String[] items, int selected,
                              PickHandler onPick) {
        View view = inflate(context, false, title, null);
        LinearLayout list = view.findViewById(R.id.layoutDialogExtra);
        AlertDialog dialog = create(context, view);
        float density = context.getResources().getDisplayMetrics().density;

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            boolean isSelected = i == selected;
            TextView row = new TextView(context);
            row.setText(items[i]);
            row.setTextSize(15);
            row.setTextColor(0xFF111111);
            row.setGravity(Gravity.CENTER);
            row.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            row.setBackgroundResource(isSelected ? R.drawable.bg_card_active : R.drawable.bg_card);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.onPick(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.round(48 * density));
            if (i > 0) {
                lp.topMargin = Math.round(8 * density);
            }
            list.addView(row, lp);
        }

        showExtra(view, items.length > 6 ? Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.45f) : 0);
        view.findViewById(R.id.btnDialogDark).setVisibility(View.GONE);
        bindButton(view.findViewById(R.id.btnDialogLight), "닫기", dialog, null);
        dialog.show();
    }

    public static void custom(Context context, String title, View content,
                              String lightText, Runnable onLight,
                              String darkText, Runnable onDark) {
        View view = inflate(context, false, title, null);
        ((LinearLayout) view.findViewById(R.id.layoutDialogExtra)).addView(content);
        showExtra(view, 0);

        AlertDialog dialog = create(context, view);
        bindButton(view.findViewById(R.id.btnDialogLight), lightText, dialog, onLight);
        bindButton(view.findViewById(R.id.btnDialogDark), darkText, dialog, onDark);
        dialog.show();
    }

    private static void showExtra(View view, int maxHeight) {
        View scroll = view.findViewById(R.id.scrollDialogExtra);
        scroll.setVisibility(View.VISIBLE);
        if (maxHeight > 0) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
            lp.height = maxHeight;
            scroll.setLayoutParams(lp);
        }
    }

    private static View inflate(Context context, boolean warning, String title, String message) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_app_confirm, null, false);

        view.findViewById(R.id.tvDialogIcon).setVisibility(warning ? View.VISIBLE : View.GONE);
        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        if (TextUtils.isEmpty(title)) {
            tvTitle.setVisibility(View.GONE);
        } else {
            tvTitle.setText(title);
        }

        TextView tvMessage = view.findViewById(R.id.tvDialogMessage);
        if (TextUtils.isEmpty(message)) {
            tvMessage.setVisibility(View.GONE);
        } else {
            tvMessage.setText(KoreanLineBreak.keepWords(message));
        }
        return view;
    }

    private static AlertDialog create(Context context, View view) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    private static void bindButton(TextView button, String text, AlertDialog dialog, Runnable action) {
        button.setText(text);
        button.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) {
                action.run();
            }
        });
    }
}
