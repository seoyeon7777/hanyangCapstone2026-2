package com.example.capstone.data;

/**
 * =========================
 * 사이즈 권장 신체 치수표
 * =========================
 *
 * 각 사이즈(S / M / L)가 어떤 신체 치수에 적합한지 정의한다. 옷의 치수가 아니라 "이 사이즈가 맞는 몸 치수의 범위"다.
 *
 * 근거: KS K 0051:2025 「성인 여성복의 치수」 (원문 표는 저작권상 복사하지 않고 숫자와 출처만 적는다)
 *  - 표 3: 피트성이 필요하지 않은 옷은 신체 치수를 5cm 간격으로 나누고 각 호칭이 호칭값 ±2.5cm를 덮는다. 여기서 쓰는 범위가 이것이다
 *  - 표 4: 일상복은 부위 하나로 사이즈를 정한다. 셔츠·니트·티셔츠 같은 상의(표 9)와 캐주얼 재킷·코트·원피스(표 8)는 가슴둘레,
 *    캐주얼 바지·스커트(표 10)는 허리둘레. 어깨너비·엉덩이둘레·허벅지둘레는 사이즈를 정하는 기준이 아니다(표 9·10에서는 참고 치수)
 *  - S/M/L 대응: 가슴둘레 호칭 85 / 90 / 95 (관행 표기 55 / 66 / 77에 해당). 하의 허리둘레는 표 9의 참고 허리둘레
 *    (가슴 85 / 90 / 95일 때 70.9 / 76.2 / 81.9cm)에 가장 가까운 호칭 70 / 75 / 80을 쓴다.
 *    이 대응은 표에 적혀 있지 않은 관행·추정이라, 판매자 치수표가 들어오면 다시 정한다
 *  - XS(가슴 80)·XL(가슴 100)은 앱의 사이즈 선택이 S/M/L뿐이라 넣지 않았다
 *
 * 정장처럼 몸에 맞게 짓는 옷(표 2, 호칭을 3cm 간격 ±1.5cm로 나눔)은 따로 다루지 않는다.
 * 나중에 products.category_id 또는 product_options API에서 실제 사이즈표를 받아오면 이 클래스만 교체하면 된다.
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
    // 상의 사이즈표 (셔츠·니트·티셔츠·재킷·코트·원피스 등)
    //
    // 가슴둘레 하나로 판단한다 (KS K 0051:2025 표 8·9, 호칭값 ±2.5cm)
    // =========================

    private static final SizeSpec[] TOP_SPECS = {

            new SizeSpec(
                    "S",
                    82.5f, 87.5f,   // 가슴둘레 (호칭 85)
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            ),

            new SizeSpec(
                    "M",
                    87.5f, 92.5f,   // 호칭 90
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            ),

            new SizeSpec(
                    "L",
                    92.5f, 97.5f,   // 호칭 95
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            )
    };


    // =========================
    // 하의 사이즈표 (캐주얼 바지·스커트)
    //
    // 허리둘레 하나로 판단한다 (KS K 0051:2025 표 10, 호칭값 ±2.5cm)
    // =========================

    private static final SizeSpec[] BOTTOM_SPECS = {

            new SizeSpec(
                    "S",
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    67.5f, 72.5f,   // 허리둘레 (호칭 70)
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            ),

            new SizeSpec(
                    "M",
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    72.5f, 77.5f,   // 호칭 75
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
            ),

            new SizeSpec(
                    "L",
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED,
                    77.5f, 82.5f,   // 호칭 80
                    NOT_USED, NOT_USED,
                    NOT_USED, NOT_USED
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


        String category = product.getCategory();

        if ("바지".equals(category) || "스커트".equals(category)) {

            return CATEGORY_BOTTOM;
        }

        if (category != null && !category.isEmpty() && !"기타".equals(category)) {

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
