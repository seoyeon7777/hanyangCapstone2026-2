
package com.example.capstone.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SizeFitChecker {

    // 임시 사이즈표의 판정 여유값(cm)
    private static final float TOLERANCE_CM = 3.0f;

    // 작은 오차 하나만으로 부적합 판정하지 않기 위한 기준
    private static final float WARNING_SCORE = 5.0f;

    private static final int NOT_COMPARED = 0;
    private static final int OK = 1;
    private static final int TOO_SMALL = 2;
    private static final int TOO_BIG = 3;

    public static FitResult check(
            Product product,
            String selectedSize
    ) {
        String productName = product != null
                ? product.getName() : "";

        UserBodyProfile profile =
                UserBodyProfile.getInstance();

        if (!profile.hasMeasurement()) {
            return result(
                    FitResult.Status.NO_PROFILE,
                    selectedSize, null, productName, null);
        }

        String category = SizeChart.categoryOf(product);
        SizeChart.SizeSpec spec =
                SizeChart.getSpec(category, selectedSize);

        if (spec == null) {
            return result(
                    FitResult.Status.NO_CHART,
                    selectedSize, null, productName, null);
        }

        List<String> reasons = new ArrayList<>();

        float[][] parts = {
                {profile.getChestCircumference(),
                        spec.chestMin, spec.chestMax},
                {profile.getShoulderWidth(),
                        spec.shoulderMin, spec.shoulderMax},
                {profile.getWaistCircumference(),
                        spec.waistMin, spec.waistMax},
                {profile.getHipCircumference(),
                        spec.hipMin, spec.hipMax},
                {profile.getThighCircumference(),
                        spec.thighMin, spec.thighMax}
        };

        String[] names = {
                "가슴둘레", "어깨너비", "허리둘레",
                "골반둘레", "허벅지둘레"
        };

        int compared = 0;
        int tight = 0;
        int loose = 0;

        float tightScore = 0f;
        float looseScore = 0f;

        for (int i = 0; i < parts.length; i++) {
            float value = parts[i][0];
            float min = parts[i][1];
            float max = parts[i][2];

            if (!valid(value, min, max)) {
                continue;
            }

            compared++;

            float deviation = distance(value, min, max);

            if (deviation <= TOLERANCE_CM) {
                continue;
            }

            float excess = deviation - TOLERANCE_CM;

            if (value > max) {
                tight++;
                tightScore += excess;

                reasons.add(String.format(
                        Locale.KOREA,
                        "%s %.1fcm · 권장 %.1f~%.1fcm (작을 수 있음)",
                        names[i], value, min, max));

            } else if (value < min) {
                loose++;
                looseScore += excess;

                reasons.add(String.format(
                        Locale.KOREA,
                        "%s %.1fcm · 권장 %.1f~%.1fcm (클 수 있음)",
                        names[i], value, min, max));
            }
        }

        if (compared == 0) {
            return result(
                    FitResult.Status.UNCHECKED,
                    selectedSize, null, productName, null);
        }

        String recommended =
                recommendSize(category, profile);

        // 비교된 값이 하나뿐인 경우:
        // 해당 부위의 차이가 충분히 큰 경우에만 경고
        float totalScore = tightScore + looseScore;

        FitResult.Status status;

        if (totalScore < WARNING_SCORE) {
            status = FitResult.Status.FIT;
            reasons.clear();

        } else if (tight > 0 && loose > 0) {
            status = FitResult.Status.MIXED;

        } else if (tight > 0) {
            status = FitResult.Status.TIGHT;

        } else if (loose > 0) {
            status = FitResult.Status.LOOSE;

        } else {
            status = FitResult.Status.FIT;
        }

        return result(
                status,
                selectedSize,
                recommended,
                productName,
                reasons
        );
    }

    public static List<FitResult> checkCart() {
        List<FitResult> warnings = new ArrayList<>();

        for (Product product : DummyData.getProducts()) {
            if (!product.isInCart() || !product.isCartSelected()) {
                continue;
            }

            FitResult fit = check(
                    product,
                    product.getSelectedSize()
            );

            if (fit.needsWarning()) {
                warnings.add(fit);
            }
        }

        return warnings;
    }

    public static String recommendSize(
            String category,
            UserBodyProfile profile
    ) {
        String bestSize = null;
        float bestScore = Float.MAX_VALUE;

        for (SizeChart.SizeSpec spec :
                SizeChart.getSpecs(category)) {

            float[][] parts = {
                    {profile.getChestCircumference(),
                            spec.chestMin, spec.chestMax},
                    {profile.getShoulderWidth(),
                            spec.shoulderMin, spec.shoulderMax},
                    {profile.getWaistCircumference(),
                            spec.waistMin, spec.waistMax},
                    {profile.getHipCircumference(),
                            spec.hipMin, spec.hipMax},
                    {profile.getThighCircumference(),
                            spec.thighMin, spec.thighMax}
            };

            float score = 0f;
            int compared = 0;

            for (float[] part : parts) {
                if (!valid(part[0], part[1], part[2])) {
                    continue;
                }

                compared++;

                float deviation = distance(
                        part[0], part[1], part[2]);

                score += Math.max(
                        0f, deviation - TOLERANCE_CM);
            }

            if (compared == 0) {
                continue;
            }

            if (score < bestScore) {
                bestScore = score;
                bestSize = spec.size;
            }
        }

        return bestSize;
    }

    private static boolean valid(
            float value, float min, float max
    ) {
        return !Float.isNaN(value)
                && !Float.isInfinite(value)
                && value > 0f
                && min != SizeChart.NOT_USED
                && max != SizeChart.NOT_USED
                && min >= 0f
                && max >= min;
    }

    private static float distance(
            float value, float min, float max
    ) {
        if (value < min) {
            return min - value;
        }

        if (value > max) {
            return value - max;
        }

        return 0f;
    }

    private static FitResult result(
            FitResult.Status status,
            String size,
            String recommended,
            String productName,
            List<String> reasons
    ) {
        return new FitResult(
                status,
                size,
                recommended,
                productName,
                reasons
        );
    }
}
