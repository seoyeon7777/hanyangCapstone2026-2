package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.capstone.R;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 치수 확인·수정 화면 (아바타 미리보기 대신).
 * 카메라가 잘못 읽은 값은 숫자를 직접 고치거나 ±0.5cm 버튼으로 조정한 뒤 저장한다.
 * 측정되지 않은 부위는 키에서 추정한 값으로 채워 두고 "추정"으로 표시한다.
 */
public class BodyEditActivity extends AppCompatActivity {

    public static final String EXTRA_HEIGHT_CM = "extra_height_cm";
    /** 부위 이름(BodyProfileStore.PART_KEYS) → cm 인 JSON. 예: {"CHEST":84.6} */
    public static final String EXTRA_PARTS_JSON = "extra_parts_json";
    /** 화면 위쪽 안내에 붙는 출처 (예: "카메라/사진 측정") */
    public static final String EXTRA_SAMPLE_LABEL = "extra_sample_label";
    /** 개발용 데모 체형 id — {@link BodyMeasurementContract} 참고 */
    public static final String EXTRA_SAMPLE_ID = "extra_sample_id";
    public static final String EXTRA_BODY_FAT = "extra_body_fat";
    public static final String EXTRA_EDIT_PROFILE_ID = "extra_edit_profile_id";
    /** 가슴두께(cm)·어깨 경사각(도): 화면에는 안 보이지만 프로필에 같이 저장돼 골격 진단 보조 값으로 쓰인다 */
    public static final String EXTRA_CHEST_DEPTH_CM = "extra_chest_depth_cm";
    public static final String EXTRA_SHOULDER_SLOPE_DEG = "extra_shoulder_slope_deg";
    /** 프로필 parts JSON 안에서의 이름 */
    public static final String PART_CHEST_DEPTH = "CHEST_DEPTH";
    public static final String PART_SHOULDER_SLOPE = "SHOULDER_SLOPE";

    private static final float STEP_CM = 0.5f;
    private static final float DEFAULT_HEIGHT_CM = 170f;
    /** 키 대비 평균 비율. 측정값이 없는 부위의 시작값이자 "이상치" 판단 기준. */
    private static final Map<String, Float> HEIGHT_RATIO = new HashMap<>();
    /** 시작값의 0.6배 미만 / 1.6배 초과면 경고 */
    private static final float WARN_LOW = 0.6f;
    private static final float WARN_HIGH = 1.6f;

    static {
        HEIGHT_RATIO.put("SHOULDER", 0.225f);
        HEIGHT_RATIO.put("CHEST", 0.52f);
        HEIGHT_RATIO.put("WAIST", 0.45f);
        HEIGHT_RATIO.put("HIP", 0.55f);
        HEIGHT_RATIO.put("ARM_LENGTH", 0.30f);
        HEIGHT_RATIO.put("LEG_LENGTH", 0.48f);
        HEIGHT_RATIO.put("NECK", 0.195f);
        HEIGHT_RATIO.put("BACK_LENGTH", 0.22f);
        HEIGHT_RATIO.put("UPPER_ARM", 0.175f);
        HEIGHT_RATIO.put("FOREARM", 0.138f);
        HEIGHT_RATIO.put("THIGH", 0.32f);
        HEIGHT_RATIO.put("CALF", 0.21f);
    }

    private enum BodyType {
        SLIM("마름", -0.8f), STANDARD("표준", 0f), HEAVY("통통", 0.6f);

        final String label;
        final float fat;

        BodyType(String label, float fat) {
            this.label = label;
            this.fat = fat;
        }
    }

    private float heightCm = DEFAULT_HEIGHT_CM;
    private String sampleLabel;
    private BodyType bodyType = BodyType.STANDARD;
    private float bodyFat = 0f;

    /** 지금 입력된 값 */
    private final Map<String, Float> values = new HashMap<>();
    /** 카메라/저장본에서 받은 처음 값. 없으면 키 비율로 추정한 값(estimated=true) */
    private final Map<String, Float> originals = new HashMap<>();
    private final Map<String, Boolean> estimated = new HashMap<>();
    private final Map<String, TextView> noteViews = new HashMap<>();
    private final Map<String, EditText> editViews = new HashMap<>();
    private final Map<BodyType, TextView> bodyTypeChips = new HashMap<>();
    private boolean updatingText;
    /** 가슴두께(cm), 어깨 경사각(도). 없으면 -1 */
    private float chestDepthCm = -1f;
    private float shoulderSlopeDeg = -1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_edit);

        heightCm = BodyMeasurementContract.heightFromIntent(getIntent(), 0f);
        if (heightCm <= 0f) {
            heightCm = getSharedPreferences("avatar_input", MODE_PRIVATE)
                    .getFloat("height_cm", DEFAULT_HEIGHT_CM);
        }
        sampleLabel = getIntent().getStringExtra(EXTRA_SAMPLE_LABEL);

        loadInitialValues();
        if (getIntent().hasExtra(EXTRA_BODY_FAT)) {
            restoreBodyFat(getIntent().getFloatExtra(EXTRA_BODY_FAT, 0f));
        }

        TextView info = findViewById(R.id.tvEditInfo);
        String line = "키 " + format(heightCm) + "cm";
        if (sampleLabel != null && !sampleLabel.isEmpty()) {
            line += " · " + sampleLabel;
        }
        info.setText(line + "\n카메라가 잘못 읽은 값은 숫자를 직접 고치거나 − / + 버튼으로 조정하세요. "
                + "줄자로 잰 값이 있다면 그 값을 넣는 것이 가장 정확해요.");

        buildRows();
        buildBodyTypeChips();

        findViewById(R.id.btnSaveBody).setOnClickListener(v -> onSaveClicked());
        BottomMenu.setup(this, BottomMenu.TAB_FITTING, this::confirmLeave);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave(() -> finish());
            }
        });
    }

    // ---------------------------------------------------------------- 초기값

    private void loadInitialValues() {
        JSONObject given = null;
        String json = BodyMeasurementContract.partsJsonFromIntent(getIntent());
        if (json != null) {
            try {
                given = new JSONObject(json);
            } catch (Exception ignored) {
            }
        }
        // 이미 저장된 프로필을 고치는 경우 JSON에서, 새 측정이면 intent에서
        chestDepthCm = given == null ? -1f : (float) given.optDouble(PART_CHEST_DEPTH, -1d);
        shoulderSlopeDeg = given == null ? -1f : (float) given.optDouble(PART_SHOULDER_SLOPE, -1d);
        if (getIntent().getFloatExtra(EXTRA_CHEST_DEPTH_CM, -1f) > 0f) {
            chestDepthCm = getIntent().getFloatExtra(EXTRA_CHEST_DEPTH_CM, -1f);
        }
        if (getIntent().getFloatExtra(EXTRA_SHOULDER_SLOPE_DEG, -1f) > 0f) {
            shoulderSlopeDeg = getIntent().getFloatExtra(EXTRA_SHOULDER_SLOPE_DEG, -1f);
        }
        for (String key : BodyProfileStore.PART_KEYS) {
            float v = given == null ? -1f : (float) given.optDouble(key, -1d);
            if (v > 0f) {
                originals.put(key, round1(v));
                values.put(key, round1(v));
                estimated.put(key, false);
            } else {
                float guess = round1(defaultCm(key));
                originals.put(key, guess);
                values.put(key, guess);
                estimated.put(key, true);
            }
        }
    }

    private float defaultCm(String key) {
        Float ratio = HEIGHT_RATIO.get(key);
        return heightCm * (ratio == null ? 0.3f : ratio);
    }

    // ---------------------------------------------------------------- 화면

    private void buildRows() {
        LinearLayout container = findViewById(R.id.editRows);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < BodyProfileStore.PART_KEYS.length; i++) {
            String key = BodyProfileStore.PART_KEYS[i];
            View row = inflater.inflate(R.layout.item_body_edit_row, container, false);
            ((TextView) row.findViewById(R.id.tvEditLabel)).setText(BodyProfileStore.PART_LABELS[i]);
            TextView note = row.findViewById(R.id.tvEditNote);
            EditText edit = row.findViewById(R.id.etEditValue);
            noteViews.put(key, note);
            editViews.put(key, edit);

            setText(edit, values.get(key));
            edit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

                @Override
                public void afterTextChanged(Editable s) {
                    if (updatingText) {
                        return;
                    }
                    try {
                        values.put(key, Float.parseFloat(s.toString()));
                    } catch (NumberFormatException e) {
                        values.put(key, -1f);
                    }
                    refreshNote(key);
                }
            });
            row.findViewById(R.id.btnEditMinus).setOnClickListener(v -> step(key, -STEP_CM));
            row.findViewById(R.id.btnEditPlus).setOnClickListener(v -> step(key, STEP_CM));
            refreshNote(key);
            container.addView(row);
        }
    }

    private void step(String key, float delta) {
        float cur = values.get(key);
        if (cur <= 0f) {
            cur = originals.get(key);
        }
        float next = Math.max(1f, round1(cur + delta));
        values.put(key, next);
        setText(editViews.get(key), next);
        refreshNote(key);
    }

    private void setText(EditText edit, Float value) {
        updatingText = true;
        edit.setText(value == null || value <= 0f ? "" : format(value));
        updatingText = false;
    }

    /** 각 줄 아래 작은 글씨: 처음 값 / 수정 여부 / 이상치 경고 */
    private void refreshNote(String key) {
        TextView note = noteViews.get(key);
        float cur = values.get(key);
        float orig = originals.get(key);
        boolean est = Boolean.TRUE.equals(estimated.get(key));
        float base = defaultCm(key);

        String text;
        int color = 0xFF999999;
        if (cur <= 0f) {
            text = "숫자를 입력해주세요";
            color = 0xFFD32F2F;
        } else if (cur < base * WARN_LOW || cur > base * WARN_HIGH) {
            text = "보통 범위를 많이 벗어났어요. 다시 확인해주세요";
            color = 0xFFD32F2F;
        } else if (Math.abs(cur - orig) >= 0.05f) {
            text = (est ? "추정 " : "측정 ") + format(orig) + "cm → 수정함";
            color = 0xFFE65100;
        } else {
            text = est ? "측정 못 함 · 직접 입력하면 사이즈 알림에 쓰여요" : "카메라 측정값";
        }
        note.setText(text);
        note.setTextColor(color);
    }

    private void buildBodyTypeChips() {
        LinearLayout row = findViewById(R.id.bodyTypeRow);
        float density = getResources().getDisplayMetrics().density;
        int padH = Math.round(14f * density);
        int padV = Math.round(7f * density);
        int gap = Math.round(6f * density);
        for (BodyType type : BodyType.values()) {
            TextView chip = new TextView(this);
            chip.setText(type.label);
            chip.setTextSize(13f);
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_chip_state);
            chip.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                bodyType = type;
                bodyFat = type.fat;
                syncChips();
            });
            row.addView(chip);
            bodyTypeChips.put(type, chip);
        }
        syncChips();
    }

    private void syncChips() {
        for (Map.Entry<BodyType, TextView> e : bodyTypeChips.entrySet()) {
            e.getValue().setSelected(e.getKey() == bodyType);
        }
    }

    private void restoreBodyFat(float fat) {
        bodyFat = Math.max(-1f, Math.min(1f, fat));
        if (bodyFat <= -0.55f) {
            bodyType = BodyType.SLIM;
        } else if (bodyFat >= 0.33f) {
            bodyType = BodyType.HEAVY;
        } else {
            bodyType = BodyType.STANDARD;
        }
    }

    // ---------------------------------------------------------------- 저장

    private void onSaveClicked() {
        for (String key : BodyProfileStore.PART_KEYS) {
            if (values.get(key) <= 0f) {
                Toast.makeText(this, "비어 있는 치수가 있어요. 숫자를 입력해주세요.",
                        Toast.LENGTH_SHORT).show();
                editViews.get(key).requestFocus();
                return;
            }
        }
        if (!LoginSession.isLoggedIn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("로그인이 필요해요")
                    .setMessage("체형 정보는 로그인 후 내 계정에 저장할 수 있어요.")
                    .setPositiveButton("로그인하기", (d, w) ->
                            startActivity(new Intent(this, LoginMainActivity.class)))
                    .setNegativeButton("취소", null)
                    .show();
            return;
        }

        BodyProfileStore.Profile editing = BodyProfileStore.find(
                this, getIntent().getStringExtra(EXTRA_EDIT_PROFILE_ID));
        String defaultName = editing != null ? editing.name : BodyProfileStore.nextDefaultName(this);
        JSONObject parts = new JSONObject();
        for (String key : BodyProfileStore.PART_KEYS) {
            // 측정도 안 했고 고치지도 않은 "키 비율 추정값"은 저장하지 않는다.
            // 저장하면 장바구니 사이즈 알림이 가짜 치수로 판정해 버린다.
            boolean untouchedGuess = Boolean.TRUE.equals(estimated.get(key))
                    && Math.abs(values.get(key) - originals.get(key)) < 0.05f;
            if (untouchedGuess) {
                continue;
            }
            try {
                parts.put(key, Math.round(values.get(key) * 10f) / 10d);
            } catch (Exception ignored) {
            }
        }
        try {
            if (chestDepthCm > 0f) {
                parts.put(PART_CHEST_DEPTH, Math.round(chestDepthCm * 10f) / 10d);
            }
            if (shoulderSlopeDeg > 0f) {
                parts.put(PART_SHOULDER_SLOPE, Math.round(shoulderSlopeDeg * 10f) / 10d);
            }
        } catch (Exception ignored) {
        }
        String typeLabel = bodyType.label;

        BodySaveConfirmDialog.show(this, heightCm, typeLabel, parts, defaultName,
                new BodySaveConfirmDialog.Listener() {
                    @Override
                    public void onSave(String name) {
                        BodyProfileStore.Profile saved = BodyProfileStore.save(
                                BodyEditActivity.this,
                                editing == null ? null : editing.id,
                                name, heightCm, bodyFat, typeLabel, parts);
                        startActivity(BodySaveCompleteActivity.newIntent(
                                BodyEditActivity.this, saved.name));
                        finish();
                    }

                    @Override
                    public void onRestartAll() {
                        Intent intent = new Intent(BodyEditActivity.this, FittingActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onRetakeCamera() {
                        Intent intent = new Intent(BodyEditActivity.this, BodyMeasurementActivity.class);
                        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    private void confirmLeave(Runnable leave) {
        View view = getLayoutInflater().inflate(R.layout.dialog_leave_confirm, null, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnLeave).setOnClickListener(v -> {
            dialog.dismiss();
            leave.run();
        });
        view.findViewById(R.id.btnStay).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ---------------------------------------------------------------- 도우미

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static String format(float value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
