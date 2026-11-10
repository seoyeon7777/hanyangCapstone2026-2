package com.example.capstone.camera;

/**
 * 카메라 측정 → 측정 위치 조정 → 결과 화면으로 넘어가는 동안 들고 있는 값.
 * 윤곽 마스크는 용량이 커서 Intent로 못 넘기므로, 가로줄마다의 몸 폭만 요약해 메모리에 둔다.
 *
 * 사용자의 사진(파일·주소)은 여기에 두지 않는다. 사진은 기기 안에서만 분석하고, 분석이 끝나면
 * clear()와 PhotoMeasurementActivity.releasePhotos()가 들고 있던 값을 모두 지운다.
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
    /** 사진 모델이 구한 가슴·허리·엉덩이 둘레(cm). 못 구했으면 null */
    public static float[] modelCirc;

    /** 가슴, 허리, 골반 순서. 정면·옆면 사진이 같은 값을 쓴다. */
    public static final float[] ratios = {DEFAULT_CHEST, DEFAULT_WAIST, DEFAULT_HIP};

    public static void resetRatios() {
        ratios[CHEST] = DEFAULT_CHEST;
        ratios[WAIST] = DEFAULT_WAIST;
        ratios[HIP] = DEFAULT_HIP;
    }

    /**
     * 설문만 한 체형 카드에서 "사진 분석 이어서 하기"로 들어왔을 때 그 카드 id. 측정 결과를 새 카드 대신 이 카드에 채운다.
     * clear()로 지우지 않는다 (사진 분석을 시작할 때마다 clear()가 불리기 때문). PhotoMeasurementActivity가 시작할 때 정한다
     */
    public static String continueProfileId;

    public static void clear() {
        modelCirc = null;
        front = null;
        side = null;
        frontLengths = null;
        resetRatios();
    }
}
