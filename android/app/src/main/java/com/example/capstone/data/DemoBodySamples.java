package com.example.capstone.data;

import android.content.Context;
import android.content.Intent;

import com.example.capstone.ui.BodyEditActivity;
import com.example.capstone.ui.MeasurementResultActivity;

/**
 * 개발·QA용 하드코딩 체형 샘플 (AI Hub 여성 실측 기반).
 * 설문/카메라를 건너뛰고 결과 화면 → 아바타로 바로 간다.
 */
public final class DemoBodySamples {

    private DemoBodySamples() {
    }

    /** 한 명의 치수. 단위 cm. */
    public static final class Sample {
        public final String id;
        public final String label;
        public final String blurb;
        public final float height;
        public final float neck;
        public final float shoulder;
        public final float chest;
        public final float waist;
        public final float hip;
        public final float upperArm;
        public final float forearm;
        public final float thigh;
        public final float calf;
        public final float armLength;
        public final float torso;      // BACK_LENGTH / 상체길이
        public final float legLength;

        public Sample(
                String id,
                String label,
                String blurb,
                float height,
                float neck,
                float shoulder,
                float chest,
                float waist,
                float hip,
                float upperArm,
                float forearm,
                float thigh,
                float calf,
                float armLength,
                float torso,
                float legLength
        ) {
            this.id = id;
            this.label = label;
            this.blurb = blurb;
            this.height = height;
            this.neck = neck;
            this.shoulder = shoulder;
            this.chest = chest;
            this.waist = waist;
            this.hip = hip;
            this.upperArm = upperArm;
            this.forearm = forearm;
            this.thigh = thigh;
            this.calf = calf;
            this.armLength = armLength;
            this.torso = torso;
            this.legLength = legLength;
        }
    }

    /**
     * AI Hub 여성 신체정보 CSV에서 체형별로 고른 실측 1명씩.
     * 생성: tmp_pick_demo_samples.py → tmp_demo_body_samples.json
     */
    public static final Sample[] ALL = {
            new Sample(
                    "AVG", "보통 / 평균",
                    "AI Hub 여성 중앙값에 가장 가까운 실측 1명 · 원본 F197",
                    158.4f, 30.2f, 32.8f, 90.0f, 74.0f, 92.3f,
                    27.7f, 22.1f, 53.9f, 33.5f, 52.8f, 36.4f, 63.8f
            ),
            new Sample(
                    "SHORT_SLIM", "키 작음 · 슬림",
                    "작은 키·가는 둘레 실측 · 원본 F406",
                    144.4f, 29.3f, 30.8f, 80.4f, 67.0f, 82.5f,
                    24.5f, 20.2f, 50.2f, 29.5f, 46.5f, 35.7f, 54.0f
            ),
            new Sample(
                    "TALL_SLIM", "키 큼 · 슬림",
                    "키 크고 허리·허벅지가 가는 실측 · 원본 F098",
                    169.0f, 29.0f, 36.5f, 76.0f, 59.5f, 86.5f,
                    25.0f, 20.7f, 50.0f, 31.9f, 53.7f, 37.9f, 69.3f
            ),
            new Sample(
                    "HOURGLASS", "모래시계",
                    "허리 대비 가슴·엉덩이 굴곡 (허벅지는 중앙 근처) · 원본 F115",
                    161.4f, 31.0f, 32.7f, 89.1f, 69.2f, 95.0f,
                    29.3f, 22.5f, 54.0f, 36.5f, 53.3f, 35.6f, 61.8f
            ),
            new Sample(
                    "PEAR", "하체 발달 (배형)",
                    "엉덩이·허벅지 발달 실측 · 원본 F026",
                    155.1f, 30.8f, 34.6f, 86.0f, 73.5f, 102.0f,
                    28.5f, 21.8f, 59.5f, 34.5f, 54.5f, 35.1f, 58.0f
            ),
            new Sample(
                    "APPLE", "상체 발달 (사과형)",
                    "허리가 굵고 엉덩이 상대↓ 실측 · 원본 F168",
                    151.1f, 35.0f, 34.0f, 101.2f, 99.8f, 92.4f,
                    29.0f, 24.4f, 52.2f, 33.0f, 49.8f, 34.8f, 57.9f
            ),
            new Sample(
                    "PETITE_HEAVY", "키 작음 · 통통",
                    "작은 키·전체 둘레 큰 실측 · 원본 F354",
                    153.8f, 35.5f, 34.4f, 108.5f, 101.3f, 112.0f,
                    36.5f, 28.6f, 63.3f, 39.5f, 53.0f, 35.4f, 58.3f
            ),
            new Sample(
                    "TALL_HEAVY", "키 큼 · 통통",
                    "키 크고 둘레 큰 실측 · 원본 F071",
                    166.5f, 39.0f, 35.4f, 103.3f, 89.7f, 109.2f,
                    38.0f, 28.6f, 70.0f, 44.0f, 57.0f, 37.3f, 64.4f
            ),
            new Sample(
                    "F163", "F163",
                    "AI Hub 원본 행 F163",
                    162.9f, 30.3f, 33.8f, 78.0f, 63.4f, 93.4f,
                    26.9f, 21.6f, 53.6f, 33.1f, 50.6f, 35.8f, 64.4f
            ),
            new Sample(
                    "F238", "F238",
                    "AI Hub 원본 행 F238",
                    167.4f, 32.0f, 34.5f, 84.6f, 68.7f, 89.9f,
                    26.0f, 22.7f, 53.4f, 34.8f, 56.0f, 35.3f, 70.2f
            ),
            new Sample(
                    "F425", "F425",
                    "AI Hub 원본 행 F425",
                    162.7f, 35.0f, 35.1f, 97.8f, 87.4f, 94.6f,
                    29.4f, 24.8f, 55.4f, 33.2f, 56.5f, 39.1f, 70.1f
            ),
            new Sample(
                    "F255", "F255",
                    "AI Hub 원본 행 F255",
                    153.1f, 33.3f, 33.3f, 94.7f, 85.3f, 92.0f,
                    31.0f, 24.7f, 53.8f, 36.3f, 49.3f, 37.4f, 59.6f
            ),
            new Sample(
                    "F407", "F407",
                    "AI Hub 원본 행 F407",
                    167.8f, 32.1f, 33.3f, 86.5f, 75.9f, 90.9f,
                    27.8f, 22.4f, 55.5f, 36.5f, 55.4f, 38.2f, 68.6f
            ),
    };

    public static Sample find(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (Sample s : ALL) {
            if (id.equalsIgnoreCase(s.id)) {
                return s;
            }
        }
        return null;
    }

    /** 측정 결과 화면용 — 카메라 extras와 동일 키. */
    public static Intent measurementResultIntent(Context context, Sample sample) {
        Intent intent = new Intent(context, MeasurementResultActivity.class);
        putCameraExtras(intent, sample);
        return intent;
    }

    /** 치수 수정 화면 직행. */
    public static Intent editIntent(Context context, Sample sample) {
        Intent intent = BodyMeasurementContract.editIntent(
                context,
                sample.height,
                sample.shoulder,
                sample.armLength,
                sample.chest,
                sample.waist,
                sample.hip,
                sample.upperArm,
                sample.forearm,
                sample.thigh,
                sample.calf,
                sample.neck,
                sample.torso,
                sample.legLength
        );
        intent.putExtra(BodyEditActivity.EXTRA_SAMPLE_ID, sample.id);
        intent.putExtra(BodyEditActivity.EXTRA_SAMPLE_LABEL, sample.label);
        return intent;
    }

    public static void putCameraExtras(Intent intent, Sample sample) {
        intent.putExtra("HEIGHT", sample.height);
        intent.putExtra("SHOULDER", sample.shoulder);
        intent.putExtra("ARM_LENGTH", sample.armLength);
        intent.putExtra("TORSO", sample.torso);
        intent.putExtra("LEG_LENGTH", sample.legLength);
        intent.putExtra("CHEST_CIRCUMFERENCE", sample.chest);
        intent.putExtra("WAIST_CIRCUMFERENCE", sample.waist);
        intent.putExtra("HIP_CIRCUMFERENCE", sample.hip);
        intent.putExtra("NECK_CIRCUMFERENCE", sample.neck);
        intent.putExtra("UPPER_ARM_CIRCUMFERENCE", sample.upperArm);
        intent.putExtra("FOREARM_CIRCUMFERENCE", sample.forearm);
        intent.putExtra("THIGH_CIRCUMFERENCE", sample.thigh);
        intent.putExtra("CALF_CIRCUMFERENCE", sample.calf);
        intent.putExtra(BodyEditActivity.EXTRA_SAMPLE_ID, sample.id);
        intent.putExtra(BodyEditActivity.EXTRA_SAMPLE_LABEL, "데모 · " + sample.label);
    }
}
