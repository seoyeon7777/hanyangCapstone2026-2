package com.example.capstone.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.capstone.R;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyPrior;
import com.example.capstone.data.BodyProfileStore;

/**
 * 사진으로 치수를 재기 직전에 키·몸무게·나이를 받는 창.
 * 설문(골격 체형 진단)에는 쓰이지 않고 치수를 어림할 때 기준값으로만 쓰이므로, 설문이 끝난 뒤 이 창에서 받는다.
 * 지난번에 넣은 값(BodyInput)이 있으면 미리 채워 두어 확인만 하면 된다.
 */
public final class BodyInputDialog {

    public interface OnDone {
        /** 값을 확인해 저장했다. heightCm는 확인한 키 */
        void onDone(float heightCm);
    }

    private BodyInputDialog() {
    }

    /**
     * @param defaultHeightCm 미리 채울 키(체형 카드에 저장된 키 등). 0 이하이면 지난번에 넣은 키를 쓴다
     */
    public static void show(Activity activity, float defaultHeightCm, OnDone onDone) {

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_body_input, null, false);
        EditText editHeight = view.findViewById(R.id.editInputHeight);
        EditText editWeight = view.findViewById(R.id.editInputWeight);
        EditText editAge = view.findViewById(R.id.editInputAge);

        float height = defaultHeightCm > 0f ? defaultHeightCm : BodyInput.height(activity);
        float weight = BodyInput.weight(activity);
        float age = BodyInput.age(activity);
        if (height > 0f) {
            editHeight.setText(trim(height));
        }
        if (weight > 0f) {
            editWeight.setText(trim(weight));
        }
        if (age > 0f) {
            editAge.setText(String.valueOf(Math.round(age)));
        }

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        view.findViewById(R.id.btnInputCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnInputOk).setOnClickListener(v -> {
            float h = readHeight(activity, editHeight);
            if (h <= 0f) {
                return;
            }
            float w = readWeight(activity, editWeight);
            if (w <= 0f) {
                return;
            }
            float a = readAge(activity, editAge);
            if (a <= 0f) {
                return;
            }

            BodyInput.save(activity, h, w, a);
            // 입력한 값으로 사이즈 알림용 치수를 바로 갱신한다
            BodyProfileStore.syncActiveProfile(activity);
            notifyIfNoEstimate(activity, h);

            dialog.dismiss();
            onDone.onDone(h);
        });

        dialog.show();
    }

    private static float readHeight(Activity activity, EditText edit) {
        String text = edit.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(activity, "키를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        float value;
        try {
            value = Float.parseFloat(text);
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "키를 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        if (value < 100f || value > 230f) {
            Toast.makeText(activity, "키를 100~230cm 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        return value;
    }

    private static float readWeight(Activity activity, EditText edit) {
        String text = edit.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(activity, "몸무게를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        float value;
        try {
            value = Float.parseFloat(text);
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "몸무게를 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        if (value < 25f || value > 150f) {
            Toast.makeText(activity, "몸무게를 25~150kg 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        return value;
    }

    private static float readAge(Activity activity, EditText edit) {
        String text = edit.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(activity, "나이를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            Toast.makeText(activity, "나이를 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        if (value < BodyPrior.INPUT_AGE_MIN || value > BodyPrior.INPUT_AGE_MAX) {
            Toast.makeText(activity, "나이를 10~99세 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        return value;
    }

    /** 입력한 값이 치수를 어림할 수 있는 범위 밖이면(저장은 된다) 사진 결과만 쓰게 된다고 미리 알린다. */
    private static void notifyIfNoEstimate(Activity activity, float heightCm) {
        BodyPrior.Result prior = BodyInput.prior(activity, heightCm);
        if (prior == null) {
            Toast.makeText(activity,
                    "입력한 값이 어림 가능한 범위 밖이라 사진 결과만 보여 드려요.",
                    Toast.LENGTH_LONG).show();
        } else if (prior.reference) {
            Toast.makeText(activity,
                    "키·나이가 확인한 범위 밖이라 어림한 치수는 참고용이에요.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** 160.0 → "160", 160.5 → "160.5" */
    private static String trim(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
    }
}
