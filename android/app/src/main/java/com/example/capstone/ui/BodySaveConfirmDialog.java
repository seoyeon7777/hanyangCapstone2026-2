package com.example.capstone.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.capstone.R;

import org.json.JSONObject;

import java.util.List;

public final class BodySaveConfirmDialog {

    public interface Listener {
        void onSave(String name);

        void onRestartAll();

        void onRetakeCamera();
    }

    private BodySaveConfirmDialog() {
    }

    public static void show(Context context, float heightCm, String bodyType, JSONObject parts,
                            String defaultName, Listener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_body_save_confirm, null, false);

        ((TextView) view.findViewById(R.id.tvSaveBodyType)).setText(bodyType);
        // 옷 구매 시 중요한 치수는 위, 나머지는 아래
        BodyValueTable.fill(view.findViewById(R.id.layoutSaveValues),
                BodyValueTable.rows(heightCm, parts, true));
        List<String[]> extraRows = BodyValueTable.extraRows(parts);
        BodyValueTable.fill(view.findViewById(R.id.layoutSaveExtraValues), extraRows);
        view.findViewById(R.id.layoutSaveExtraValues)
                .setVisibility(extraRows.isEmpty() ? View.GONE : View.VISIBLE);

        EditText etName = view.findViewById(R.id.etSaveName);
        etName.setText(defaultName);
        etName.setSelection(etName.length());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.btnSaveConfirm).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            dialog.dismiss();
            listener.onSave(name.isEmpty() ? defaultName : name);
        });
        view.findViewById(R.id.btnSaveRemeasure).setOnClickListener(v -> {
            dialog.dismiss();
            showRemeasureChoice(context, listener);
        });

        dialog.show();
    }

    private static void showRemeasureChoice(Context context, Listener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_remeasure_choice, null, false);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.btnRestartAll).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onRestartAll();
        });
        view.findViewById(R.id.btnRetakeCamera).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onRetakeCamera();
        });
        view.findViewById(R.id.btnKeepPreview).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
