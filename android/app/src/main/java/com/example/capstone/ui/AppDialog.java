package com.example.capstone.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.capstone.R;

public final class AppDialog {

    public interface InputHandler {
        boolean onInput(String text);
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
        View view = inflate(context, false, title, message);
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

    private static View inflate(Context context, boolean warning, String title, String message) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_app_confirm, null, false);

        view.findViewById(R.id.tvDialogIcon).setVisibility(warning ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.tvDialogTitle)).setText(title);

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
