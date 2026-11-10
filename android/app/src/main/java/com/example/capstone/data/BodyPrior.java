package com.example.capstone.data;

/**
 * 키·몸무게·나이로 "평균 체형"의 둘레를 어림한다 (사진 없이).
 *
 * 계수는 사이즈코리아 5~8차 여성 직접측정(약 1.5만 명)으로 맞춘 일차식이고,
 * 학습할 때 몸무게·키 입력에 평균 0의 오차를 섞어 사용자가 직접 입력한 값의 오차를 흉내 냈다.
 * 근거와 검증은 저장소의 training/README.md 9장에 있다.
 *
 * 값 = 절편 + 키(cm) × 계수 + 몸무게(kg) × 계수 + 나이 × 계수
 * 나잇대마다 계수를 따로 두었다(서로 영향이 없게). 학습한 범위를 벗어난 입력은 계산하지 않는다(null).
 *
 * 항목은 사이즈코리아 표준 정의: 젖가슴둘레(가슴이 가장 나온 곳), 허리둘레, 엉덩이둘레, 넙다리둘레.
 */
public final class BodyPrior {

    public static final int CHEST = 0;
    public static final int WAIST = 1;
    public static final int HIP = 2;
    public static final int THIGH = 3;

    /** BodyProfileStore.PART_KEYS 이름. 위 인덱스와 순서가 같다. */
    public static final String[] PART_KEYS = {"CHEST", "WAIST", "HIP", "THIGH"};

    /** 입력 안내용 허용 범위(이 밖이면 계산하지 않는다). */
    public static final float INPUT_AGE_MIN = 10f;
    public static final float INPUT_AGE_MAX = 99f;

    /**
     * 성인(19~79세)이 어림값을 받을 수 있는 키 범위. 학습 자료(사이즈코리아 8차, 키 140~181cm)로 확인한 범위는
     * {@link #ADULT_KEY_CHECKED_MIN}~{@link #ADULT_KEY_CHECKED_MAX}이고, 이 밖은 일차식을 그대로 늘려 쓰는 것이라 "참고용"으로 표시한다.
     * (키가 가운데인 사람으로만 학습해 작은 키·큰 키에 적용해도 오차가 더 커지지 않았다. training/README.md 9-10)
     */
    public static final float ADULT_KEY_MIN = 130f;
    public static final float ADULT_KEY_MAX = 200f;
    public static final float ADULT_KEY_CHECKED_MIN = 135f;
    public static final float ADULT_KEY_CHECKED_MAX = 190f;

    /** 사진 모델 값이 기준선과 이만큼(cm) 넘게 다르면 모델이 틀린 것으로 본다 (학습 자료의 보정 폭 표준편차는 약 4cm) */
    public static final float MODEL_MAX_DIFF_CM = 15f;

    private BodyPrior() {
    }

    private static final class Group {
        final int ageMin;
        final int ageMax;
        final float keyMin;
        final float keyMax;
        final float kgMin;
        final float kgMax;
        /** [부위][절편, 키, 몸무게, 나이] */
        final float[][] coef;
        /** 같은 그룹 교차검증 평균 오차(cm) — 부위 순서 */
        final float[] mae;

        Group(int ageMin, int ageMax, float keyMin, float keyMax, float kgMin, float kgMax,
              float[][] coef, float[] mae) {
            this.ageMin = ageMin;
            this.ageMax = ageMax;
            this.keyMin = keyMin;
            this.keyMax = keyMax;
            this.kgMin = kgMin;
            this.kgMax = kgMax;
            this.coef = coef;
            this.mae = mae;
        }
    }

    // 10~12세 / 13~18세 / 19~79세 (2004~2015년 조사 자료. 10~12세 허리의 나이 계수가 음수인 것은 두 조사에서 일관됨)
    private static final Group G_10_12 = new Group(10, 12, 120f, 190f, 25f, 120f,
            new float[][]{
                    {50.5908f, -0.0976f, 0.8331f, 0.1808f},
                    {79.7070f, -0.3101f, 0.9671f, -0.9119f},
                    {37.7312f, 0.0546f, 0.7116f, 0.4419f},
                    {34.6283f, -0.0670f, 0.5758f, -0.0140f}},
            new float[]{1.95f, 2.11f, 1.61f, 1.59f});

    private static final Group G_13_18 = new Group(13, 18, 135f, 190f, 30f, 120f,
            new float[][]{
                    {67.3808f, -0.1832f, 0.7401f, 0.2600f},
                    {71.9442f, -0.2722f, 0.7709f, -0.0908f},
                    {52.8301f, 0.0027f, 0.5901f, 0.4017f},
                    {38.8415f, -0.0738f, 0.4709f, 0.1378f}},
            new float[]{2.41f, 2.31f, 1.75f, 1.67f});

    private static final Group G_19_79 = new Group(19, 79, ADULT_KEY_MIN, ADULT_KEY_MAX, 35f, 120f,
            new float[][]{
                    {81.8554f, -0.2593f, 0.7417f, 0.1177f},
                    {77.0192f, -0.3637f, 0.8587f, 0.2033f},
                    {62.6130f, 0.0028f, 0.5509f, -0.0367f},
                    {41.7391f, -0.0476f, 0.4147f, -0.0716f}},
            new float[]{2.6f, 2.8f, 2.0f, 2.0f});

    /** 19세 이상 나잇대별 평균 오차(젖가슴, 허리, 엉덩이, 넙다리). 70대는 허리·넙다리 오차가 크다. */
    private static float[] adultMae(int age) {
        if (age < 30) {
            return new float[]{2.4f, 2.6f, 1.8f, 1.7f};
        } else if (age < 50) {
            return new float[]{2.6f, 2.8f, 2.0f, 1.9f};
        } else if (age < 70) {
            return new float[]{2.7f, 2.9f, 2.0f, 2.3f};
        }
        return new float[]{2.8f, 4.3f, 2.3f, 3.0f};
    }

    public static final class Result {
        /** 추정 둘레(cm). 순서는 CHEST, WAIST, HIP, THIGH */
        public final float[] values;
        /** 같은 나잇대 평균 오차(cm). 입력이 정확할 때 기준이고, 입력이 틀리면 더 커진다 */
        public final float[] mae;
        /** 80세 이상이거나 성인 키가 확인한 범위(135~190cm) 밖이라 참고용인 경우 */
        public final boolean reference;
        /** 19세 이상(사진 모델은 성인 자료로 학습해서 성인에게만 쓴다) */
        public final boolean adult;

        Result(float[] values, float[] mae, boolean reference, boolean adult) {
            this.values = values;
            this.mae = mae;
            this.reference = reference;
            this.adult = adult;
        }

        public float value(String partKey) {
            int i = indexOf(partKey);
            return i < 0 ? -1f : values[i];
        }

        public float mae(String partKey) {
            int i = indexOf(partKey);
            return i < 0 ? -1f : mae[i];
        }
    }

    /** BodyProfileStore.PART_KEYS 이름이 기준선이 다루는 부위면 인덱스, 아니면 -1 */
    public static int indexOf(String partKey) {
        for (int i = 0; i < PART_KEYS.length; i++) {
            if (PART_KEYS[i].equals(partKey)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 키(cm)·몸무게(kg)·나이로 치수를 어림한다.
     * 입력이 없거나(0 이하) 계산할 수 있는 범위(나이 10~99, 성인 키 130~200cm 등) 밖이면 null.
     * 성인 키가 135~190cm 밖이거나 80세 이상이면 reference=true(참고용).
     */
    public static Result estimate(float heightCm, float weightKg, float age) {
        if (!(heightCm > 0f) || !(weightKg > 0f) || !(age > 0f)) {
            return null;
        }
        int a = Math.round(age);
        Group g;
        boolean reference = false;
        if (a >= 10 && a <= 12) {
            g = G_10_12;
        } else if (a >= 13 && a <= 18) {
            g = G_13_18;
        } else if (a >= 19 && a <= 79) {
            g = G_19_79;
        } else if (a >= 80 && a <= (int) INPUT_AGE_MAX) {
            g = G_19_79;       // 80세 이상은 자료가 없어 79세 값으로 어림하고 참고용으로 표시
            a = 79;
            reference = true;
        } else {
            return null;
        }
        if (heightCm < g.keyMin || heightCm > g.keyMax || weightKg < g.kgMin || weightKg > g.kgMax) {
            return null;
        }
        if (g == G_19_79 && (heightCm < ADULT_KEY_CHECKED_MIN || heightCm > ADULT_KEY_CHECKED_MAX)) {
            reference = true;  // 확인한 키 범위 밖은 일차식을 늘려 쓴 값이라 참고용
        }
        float[] values = new float[PART_KEYS.length];
        for (int i = 0; i < values.length; i++) {
            float[] c = g.coef[i];
            values[i] = c[0] + c[1] * heightCm + c[2] * weightKg + c[3] * a;
        }
        float[] mae = (g == G_19_79) ? adultMae(a) : g.mae.clone();
        return new Result(values, mae, reference, g == G_19_79);
    }

    /**
     * 사진으로 잰 둘레(가슴, 허리, 엉덩이, 허벅지 순서; 없으면 0 이하)를 기준선과 맞춘다.
     * 값이 없거나 기준선과 너무 다르면 기준선 값으로 바꾸고 estimatedOut[i]를 true로 한다.
     * 기준선(prior)이 없으면 측정값을 그대로 돌려준다.
     */
    public static float[] reconcile(Result prior, float[] measured, boolean[] estimatedOut) {
        return reconcile(prior, measured, estimatedOut, null);
    }

    /**
     * 위와 같되, trusted[i]가 true인 부위는(사진 모델이 구한 값) 기준선과 많이 달라도 그대로 쓴다.
     * 사진 모델은 기준선을 보정하려고 만든 것이라, 기준선과 다르다는 이유로 버리면 안 된다.
     * 다만 기준선과 {@link #MODEL_MAX_DIFF_CM} 넘게 다르면 모델이 틀린 것으로 보고 기준선을 쓴다.
     */
    public static float[] reconcile(Result prior, float[] measured, boolean[] estimatedOut, boolean[] trusted) {
        float[] out = new float[PART_KEYS.length];
        for (int i = 0; i < out.length; i++) {
            float m = i < measured.length ? measured[i] : -1f;
            boolean isTrusted = trusted != null && i < trusted.length && trusted[i];
            boolean useEstimate;
            if (prior == null) {
                useEstimate = false;
            } else if (!(m > 0f)) {
                useEstimate = true;
            } else if (isTrusted) {
                useEstimate = Math.abs(m - prior.values[i]) > MODEL_MAX_DIFF_CM;
            } else {
                useEstimate = isImplausible(prior, PART_KEYS[i], m);
            }
            out[i] = useEstimate ? prior.values[i] : m;
            if (estimatedOut != null && i < estimatedOut.length) {
                estimatedOut[i] = useEstimate;
            }
        }
        return out;
    }

    /**
     * 사진·카메라로 잰 값이 기준선과 너무 다르면 true (옷의 두께, 팔, 자세 때문에 크게 틀린 값일 가능성).
     * 기준은 평균 오차의 2.5배(최소 5cm).
     */
    public static boolean isImplausible(Result prior, String partKey, float measuredCm) {
        int i = indexOf(partKey);
        if (prior == null || i < 0 || !(measuredCm > 0f)) {
            return false;
        }
        float limit = Math.max(5f, 2.5f * prior.mae[i]);
        return Math.abs(measuredCm - prior.values[i]) > limit;
    }
}
