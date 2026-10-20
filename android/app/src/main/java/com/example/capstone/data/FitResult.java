package com.example.capstone.data;

import java.util.ArrayList;
import java.util.List;

/**
 * =========================
 * 사이즈 적합도 판정 결과
 * =========================
 *
 * SizeFitChecker가 만들어서 돌려준다.
 */
public class FitResult {

    // =========================
    // 판정 상태
    // =========================

    public enum Status {

        /** 체형 측정 정보가 없어서 판단할 수 없음 (경고함) */
        NO_PROFILE,

        /** 체형 정보가 없어서 검사를 건너뜀 (경고하지 않음) */
        UNCHECKED,

        /** 사이즈표가 없어서 판단할 수 없음 */
        NO_CHART,

        /** 선택한 사이즈가 적합 */
        FIT,

        /** 선택한 사이즈가 작을 수 있음 */
        TIGHT,

        /** 선택한 사이즈가 클 수 있음 */
        LOOSE,

        /** 부위에 따라 작기도 하고 크기도 함 */
        MIXED
    }


    private final Status status;

    /** 사용자가 선택한 사이즈 */
    private final String selectedSize;

    /** 신체 치수에 가장 가까운 사이즈 (없으면 null) */
    private final String recommendedSize;

    /** 상품명 (여러 상품을 한 번에 검사할 때 사용) */
    private final String productName;

    /** 부위별 비교 결과 문구 */
    private final List<String> reasons;


    public FitResult(
            Status status,
            String selectedSize,
            String recommendedSize,
            String productName,
            List<String> reasons
    ) {
        this.status = status;
        this.selectedSize = selectedSize;
        this.recommendedSize = recommendedSize;
        this.productName = productName;

        this.reasons =
                reasons != null
                        ? reasons
                        : new ArrayList<>();
    }


    // =========================
    // 경고 팝업을 띄워야 하는 상태인지
    //
    // 사이즈표가 없는 경우는 판단 불가이므로
    // 굳이 사용자를 막지 않는다.
    // =========================

    public boolean needsWarning() {

        return status == Status.NO_PROFILE
                || status == Status.TIGHT
                || status == Status.LOOSE
                || status == Status.MIXED;
    }


    // =========================
    // 상태 요약 문구
    // =========================

    public String statusLabel() {

        switch (status) {

            case TIGHT:
                return "작을 수 있음";

            case LOOSE:
                return "클 수 있음";

            case MIXED:
                return "부위별로 맞지 않음";

            case NO_PROFILE:
                return "확인 불가";

            case UNCHECKED:
                return "검사하지 않음";

            case NO_CHART:
                return "사이즈표 없음";

            case FIT:
            default:
                return "적합";
        }
    }


    // =========================
    // Getter
    // =========================

    public Status getStatus() {
        return status;
    }

    public String getSelectedSize() {
        return selectedSize;
    }

    public String getRecommendedSize() {
        return recommendedSize;
    }

    public String getProductName() {
        return productName;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
