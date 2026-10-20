package com.example.capstone.data;

/**
 * =========================
 * 사이즈 권장 신체 치수표
 * =========================
 *
 * 각 사이즈(S / M / L)가 어떤 신체 치수에 적합한지 정의한다.
 *
 * 지금은 상의 / 하의 두 가지 기준만 가지고 있고,
 * 나중에 products.category_id 또는 product_options API에서
 * 실제 사이즈표를 받아오면 이 클래스만 교체하면 된다.
 */
public class SizeChart {

    // =========================
    // 카테고리 구분
    // =========================

    public static final String CATEGORY_TOP = "TOP";
    public static final String CATEGORY_BOTTOM = "BOTTOM";


    // =========================
    // 값이 없는 부위 표시
    // =========================

    public static final float NOT_USED = -1f;


    // =========================
    // 사이즈 하나에 대한 권장 치수
    //
    // 사용하지 않는 부위는 NOT_USED
    // =========================

    public static class SizeSpec {

        public final String size;

        public final float chestMin;
        public final float chestMax;

        public final float shoulderMin;
        public final float shoulderMax;

        public final float waistMin;
        public final float waistMax;

        public final float hipMin;
        public final float hipMax;

        public final float thighMin;
        public final float thighMax;


        public SizeSpec(
                String size,
                float chestMin,
                float chestMax,
                float shoulderMin,
                float shoulderMax,
                float waistMin,
                float waistMax,
                float hipMin,
                float hipMax,
                float thighMin,
                float thighMax
        ) {
            this.size = size;

            this.chestMin = chestMin;
            this.chestMax = chestMax;

            this.shoulderMin = shoulderMin;
            this.shoulderMax = shoulderMax;

            this.waistMin = waistMin;
            this.waistMax = waistMax;

            this.hipMin = hipMin;
            this.hipMax = hipMax;

            this.thighMin = thighMin;
            this.thighMax = thighMax;
        }
    }


    // =========================
    // 상품 상세에서 선택 가능한 사이즈
    // =========================

    public static final String[] SIZES = {
            "S",
            "M",
            "L"
    };


    // =========================
    // 상의 사이즈표
    //
    // 가슴둘레 / 어깨너비 기준
    // =========================

    private static final SizeSpec[] TOP_SPECS = {

            new SizeSpec(
                    "S",
                    82f, 90f,       // 가슴둘레
                    40f, 43.5f,     // 어깨너비
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            ),

            new SizeSpec(
                    "M",
                    90f, 98f,
                    43.5f, 47f,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            ),

            new SizeSpec(
                    "L",
                    98f, 106f,
                    47f, 50.5f,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            )
    };


    // =========================
    // 하의 사이즈표
    //
    // 허리둘레 / 골반둘레 / 허벅지둘레 기준
    // =========================

    private static final SizeSpec[] BOTTOM_SPECS = {

            new SizeSpec(
                    "S",
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    66f, 74f,       // 허리둘레
                    86f, 94f,       // 골반둘레
                    46f, 52f        // 허벅지둘레
            ),

            new SizeSpec(
                    "M",
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    74f, 82f,
                    94f, 102f,
                    52f, 57f
            ),

            new SizeSpec(
                    "L",
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    82f, 90f,
                    102f, 110f,
                    57f, 62f
            )
    };


    // =========================
    // 상품이 상의인지 하의인지 판단
    //
    // 지금은 DummyData에 category_id가 없으므로
    // 상품명 키워드로 임시 판단한다.
    //
    // 나중에 product.getCategoryId()로 교체하면 된다.
    // =========================

    private static final String[] BOTTOM_KEYWORDS = {
            "팬츠",
            "바지",
            "데님",
            "진",
            "슬랙스",
            "조거",
            "쇼츠",
            "반바지",
            "스커트",
            "치마"
    };


    public static String categoryOf(Product product) {

        if (product == null
                || product.getName() == null) {

            return CATEGORY_TOP;
        }


        String name = product.getName();

        for (String keyword : BOTTOM_KEYWORDS) {

            if (name.contains(keyword)) {

                return CATEGORY_BOTTOM;
            }
        }

        return CATEGORY_TOP;
    }


    // =========================
    // 카테고리 전체 사이즈표
    // =========================

    public static SizeSpec[] getSpecs(String category) {

        if (CATEGORY_BOTTOM.equals(category)) {

            return BOTTOM_SPECS;
        }

        return TOP_SPECS;
    }


    // =========================
    // 특정 사이즈의 권장 치수
    //
    // 사이즈표에 없으면 null
    // =========================

    public static SizeSpec getSpec(
            String category,
            String size
    ) {

        if (size == null) {
            return null;
        }


        for (SizeSpec spec : getSpecs(category)) {

            if (spec.size.equalsIgnoreCase(size.trim())) {

                return spec;
            }
        }

        return null;
    }


    // =========================
    // 카테고리 이름 (화면 표시용)
    // =========================

    public static String categoryLabel(String category) {

        if (CATEGORY_BOTTOM.equals(category)) {

            return "하의";
        }

        return "상의";
    }
}
