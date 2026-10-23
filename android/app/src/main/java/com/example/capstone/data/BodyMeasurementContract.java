package com.example.capstone.data;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * 팀원 설문·카메라 결과 → {@link com.example.capstone.ui.BodyEditActivity} 연결 계약.
 *
 * <p>MeasurementResultActivity 등에서 아래 extras / parts JSON 으로 넘긴다.
 * 카메라 쪽: NECK_CIRCUMFERENCE→NECK, TORSO→BACK_LENGTH, LEG_LENGTH→LEG_LENGTH.
 */
public final class BodyMeasurementContract {

    private BodyMeasurementContract() {
    }

    /** 팀원 FittingActivity → BodyTypeActivity 와 동일. */
    public static final String EXTRA_USER_HEIGHT_CM = "USER_HEIGHT_CM";

    // ---- 결과 화면 라벨과 맞춘 cm extras (선택) ----
    public static final String EXTRA_SHOULDER_CM = "shoulder_cm";
    public static final String EXTRA_ARM_LENGTH_CM = "arm_length_cm";
    public static final String EXTRA_CHEST_CM = "chest_cm";
    public static final String EXTRA_WAIST_CM = "waist_cm";
    public static final String EXTRA_HIP_CM = "hip_cm";
    public static final String EXTRA_UPPER_ARM_CM = "upper_arm_cm";
    public static final String EXTRA_FOREARM_CM = "forearm_cm";
    public static final String EXTRA_THIGH_CM = "thigh_cm";
    public static final String EXTRA_CALF_CM = "calf_cm";
    public static final String EXTRA_NECK_CM = "neck_cm";
    public static final String EXTRA_TORSO_CM = "torso_cm";       // → BACK_LENGTH
    public static final String EXTRA_LEG_LENGTH_CM = "leg_length_cm";

    /** 한 JSON으로 넘길 때. BodyPart name 키 사용. */
    public static final String EXTRA_PARTS_JSON = "extra_parts_json";

    public static Intent editIntent(
            Context context,
            float heightCm,
            Float shoulder,
            Float armLength,
            Float chest,
            Float waist,
            Float hip,
            Float upperArm,
            Float forearm,
            Float thigh,
            Float calf,
            Float neck,
            Float torso,
            Float legLength
    ) {
        Intent intent = new Intent();
        intent.setClassName(context, "com.example.capstone.ui.BodyEditActivity");
        intent.putExtra("extra_height_cm", heightCm);
        try {
            JSONObject parts = new JSONObject();
            put(parts, "SHOULDER", shoulder);
            put(parts, "ARM_LENGTH", armLength);
            put(parts, "CHEST", chest);
            put(parts, "WAIST", waist);
            put(parts, "HIP", hip);
            put(parts, "UPPER_ARM", upperArm);
            put(parts, "FOREARM", forearm);
            put(parts, "THIGH", thigh);
            put(parts, "CALF", calf);
            put(parts, "NECK", neck);
            put(parts, "BACK_LENGTH", torso);
            put(parts, "LEG_LENGTH", legLength);
            if (parts.length() > 0) {
                intent.putExtra(EXTRA_PARTS_JSON, parts.toString());
            }
        } catch (Exception ignored) {
        }
        return intent;
    }

    /** Intent에 흩어진 *_cm extras → parts JSON. */
    public static String partsJsonFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        String existing = intent.getStringExtra(EXTRA_PARTS_JSON);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        if (intent.hasExtra("extra_parts_json")) {
            String alt = intent.getStringExtra("extra_parts_json");
            if (alt != null && !alt.isEmpty()) {
                return alt;
            }
        }
        try {
            JSONObject parts = new JSONObject();
            put(parts, "SHOULDER", readCm(intent, EXTRA_SHOULDER_CM));
            put(parts, "ARM_LENGTH", readCm(intent, EXTRA_ARM_LENGTH_CM));
            put(parts, "CHEST", readCm(intent, EXTRA_CHEST_CM));
            put(parts, "WAIST", readCm(intent, EXTRA_WAIST_CM));
            put(parts, "HIP", readCm(intent, EXTRA_HIP_CM));
            put(parts, "UPPER_ARM", readCm(intent, EXTRA_UPPER_ARM_CM));
            put(parts, "FOREARM", readCm(intent, EXTRA_FOREARM_CM));
            put(parts, "THIGH", readCm(intent, EXTRA_THIGH_CM));
            put(parts, "CALF", readCm(intent, EXTRA_CALF_CM));
            put(parts, "NECK", readCm(intent, EXTRA_NECK_CM));
            put(parts, "BACK_LENGTH", readCm(intent, EXTRA_TORSO_CM));
            put(parts, "LEG_LENGTH", readCm(intent, EXTRA_LEG_LENGTH_CM));
            return parts.length() > 0 ? parts.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static float heightFromIntent(Intent intent, float fallback) {
        if (intent == null) {
            return fallback;
        }
        float h = intent.getFloatExtra("extra_height_cm", 0f);
        if (h > 1f) {
            return h;
        }
        h = intent.getFloatExtra(EXTRA_USER_HEIGHT_CM, 0f);
        return h > 1f ? h : fallback;
    }

    private static Float readCm(Intent intent, String key) {
        if (!intent.hasExtra(key)) {
            return null;
        }
        float v = intent.getFloatExtra(key, Float.NaN);
        return Float.isNaN(v) || v <= 0f ? null : v;
    }

    private static void put(JSONObject parts, String name, Float cm) throws Exception {
        if (cm != null && cm > 0f) {
            parts.put(name, cm);
        }
    }
}
