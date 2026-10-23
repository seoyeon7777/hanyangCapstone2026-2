package com.example.capstone.camera;

import android.net.Uri;

import java.io.File;

/**
 * 카메라 측정 → 측정 위치 조정 → 결과 화면으로 넘어가는 동안 들고 있는 값.
 * 윤곽 마스크는 용량이 커서 Intent로 못 넘기므로, 가로줄마다의 몸 폭만 요약해 메모리에 둔다.
 */
public final class MeasurementSession {

    private MeasurementSession() {
    }

    /** 가슴·허리·골반 기본 측정 위치 (몸 높이 대비 비율, 머리 위 0 ~ 발바닥 1) */
    public static final float DEFAULT_CHEST = 0.29f;
    public static final float DEFAULT_WAIST = 0.43f;
    public static final float DEFAULT_HIP = 0.52f;

    public static final int CHEST = 0;
    public static final int WAIST = 1;
    public static final int HIP = 2;
    public static final String[] LABELS = {"가슴", "허리", "골반"};

    /** 한 사진의 사람 윤곽을 가로줄(y)마다 요약한 것. 마스크 해상도 기준. */
    public static final class Silhouette {
        public final int width;
        public final int height;
        /** 사람이 처음/마지막으로 나오는 줄 */
        public final int top;
        public final int bottom;
        /** 줄마다 화면 가운데에 가장 가까운 사람 덩어리의 왼쪽/오른쪽 x. 없으면 -1 */
        public final int[] left;
        public final int[] right;

        public Silhouette(int width, int height, int top, int bottom, int[] left, int[] right) {
            this.width = width;
            this.height = height;
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
        }

        public int rowAt(float ratio) {
            int row = top + Math.round(ratio * (bottom - top));
            return Math.max(0, Math.min(height - 1, row));
        }

        /** ratio 높이에서의 몸 폭(px). 사람이 없으면 0 */
        public float widthPx(float ratio) {
            int row = rowAt(ratio);
            if (left[row] < 0 || right[row] < 0) {
                return 0f;
            }
            return right[row] - left[row] + 1;
        }

        public float widthCm(float heightCm, float ratio) {
            return widthPx(ratio) * heightCm / (bottom - top);
        }
    }

    public static Silhouette front;
    public static Silhouette side;
    public static BodyMeasurementCalculator.Result frontLengths;
    public static float heightCm;
    public static File frontPhoto;
    public static File sidePhoto;
    /** 앨범에서 고른 사진이면 파일 대신 Uri */
    public static Uri frontUri;
    public static Uri sideUri;
    /** 가슴, 허리, 골반 순서. 정면·옆면 사진이 같은 값을 쓴다. */
    public static final float[] ratios = {DEFAULT_CHEST, DEFAULT_WAIST, DEFAULT_HIP};

    public static void resetRatios() {
        ratios[CHEST] = DEFAULT_CHEST;
        ratios[WAIST] = DEFAULT_WAIST;
        ratios[HIP] = DEFAULT_HIP;
    }

    public static void clear() {
        front = null;
        side = null;
        frontLengths = null;
        frontPhoto = null;
        sidePhoto = null;
        frontUri = null;
        sideUri = null;
        resetRatios();
    }
}
