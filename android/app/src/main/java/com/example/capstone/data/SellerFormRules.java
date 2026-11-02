package com.example.capstone.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 판매자 등록 화면의 입력 값 해석 규칙. 화면과 따로 두어 단위 테스트로 확인한다. 최종 검증은 서버가 한다. */
public final class SellerFormRules {

    /** 서버와 같은 사이즈 순서 (recommend/rec_config.py SELLER_SIZES) */
    public static final List<String> SIZE_ORDER = Arrays.asList("XS", "S", "M", "L", "XL", "FREE");
    public static final int MAX_PRICE = 10_000_000;

    private SellerFormRules() {
    }

    /** "39,000" "39000원" 같은 입력 → 정수. 숫자가 아니거나 범위 밖이면 -1. */
    public static int parsePrice(String input) {
        if (input == null) {
            return -1;
        }
        String digits = input.replaceAll("[,\\s원]", "");
        if (digits.isEmpty() || digits.length() > 9 || !digits.matches("[0-9]+")) {
            return -1;
        }
        int value = Integer.parseInt(digits);
        return value >= 1 && value <= MAX_PRICE ? value : -1;
    }

    /** "46.5" "46,5" → 46.5. 숫자가 아니면 NaN. */
    public static double parseCm(String input) {
        if (input == null) {
            return Double.NaN;
        }
        String text = input.trim().replace(',', '.');
        if (!text.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) {
            return Double.NaN;
        }
        return Double.parseDouble(text);
    }

    /** 고른 사이즈를 서버와 같은 순서(XS→FREE)로 정렬한다. */
    public static List<String> sortSizes(List<String> sizes) {
        List<String> out = new ArrayList<>(sizes);
        out.sort((a, b) -> SIZE_ORDER.indexOf(a) - SIZE_ORDER.indexOf(b));
        return out;
    }

    /** 혼용률 퍼센트 입력(빈 칸은 건너뜀)의 합이 정확히 100인지 */
    public static boolean compositionSumsTo100(List<Double> percents) {
        double sum = 0;
        for (Double p : percents) {
            if (p != null && !p.isNaN()) {
                sum += p;
            }
        }
        return Math.abs(sum - 100.0) < 0.01;
    }

    /** 색상 직접 입력 → 서버 규칙(영문 대문자 1~30자)에 맞춰 다듬는다. 빈 입력은 null. */
    public static String normalizeColor(String input) {
        if (input == null) {
            return null;
        }
        String color = input.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        return color.isEmpty() || color.length() > 30 ? null : color;
    }
}
