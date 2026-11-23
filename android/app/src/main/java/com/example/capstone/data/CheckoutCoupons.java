package com.example.capstone.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// [여기에 DB 입력] 보유 쿠폰 조회·사용 처리 (user_coupons, coupons)
public final class CheckoutCoupons {

    public static final String TYPE_MALL = "쇼핑몰";
    public static final String TYPE_BRAND = "브랜드";
    public static final String TYPE_ITEM = "상품";
    public static final String[] TYPES = {TYPE_MALL, TYPE_BRAND, TYPE_ITEM};

    /** 쿠폰 적용 범위를 따질 때 쓰는 주문 상품 한 줄 (이름·분류·신상 여부·줄 금액) */
    public static final class Line {
        public final String name;
        public final String category;
        public final boolean isNew;
        public final int price;

        public Line(String name, String category, boolean isNew, int price) {
            this.name = name == null ? "" : name;
            this.category = category == null ? "" : category;
            this.isNew = isNew;
            this.price = price;
        }
    }

    /** 이 쿠폰을 쓸 수 있는 상품인지 */
    interface Scope {
        boolean matches(Line line);
    }

    private static final Scope ANY = line -> true;
    private static final Scope NEW_ONLY = line -> line.isNew;
    /** 긴소매 상의: 상의 중 반팔·민소매가 아닌 것 (니트·셔츠·맨투맨·후드 등) */
    private static final Scope LONG_SLEEVE_TOP = line -> "상의".equals(line.category)
            && !line.name.contains("반팔") && !line.name.contains("민소매") && !line.name.contains("나시");

    public static final class Coupon {
        public final String id;
        public final String type;
        public final String name;
        public final int percent;
        public final int maxDiscount;
        public final int minOrder;
        public final String conditionText;
        public final String scopeText;
        private final Scope scope;
        private final int endYear, endMonth, endDay;

        Coupon(String id, String type, String name, int percent, int maxDiscount, int minOrder,
               String conditionText, Scope scope, String scopeText, int endYear, int endMonth, int endDay) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.conditionText = conditionText;
            this.scope = scope;
            this.scopeText = scopeText;
            this.percent = percent;
            this.maxDiscount = maxDiscount;
            this.minOrder = minOrder;
            this.endYear = endYear;
            this.endMonth = endMonth;
            this.endDay = endDay;
        }

        public boolean isExpired(Calendar now) {
            Calendar end = Calendar.getInstance();
            end.clear();
            end.set(endYear, endMonth - 1, endDay, 23, 59, 59);
            return now.after(end);
        }

        /** 이 쿠폰을 쓸 수 있는 상품이 주문에 하나라도 있는지 */
        public boolean appliesToAny(List<Line> lines) {
            for (Line line : lines) {
                if (scope.matches(line)) {
                    return true;
                }
            }
            return false;
        }

        /** 할인 금액: 쿠폰을 쓸 수 있는 상품의 금액만으로 계산한다 (쓸 수 있는 상품이 없거나 최소 주문 금액에 못 미치면 0) */
        public int discountFor(List<Line> lines) {
            int eligible = 0;
            for (Line line : lines) {
                if (scope.matches(line)) {
                    eligible += line.price;
                }
            }
            if (eligible <= 0 || eligible < minOrder) {
                return 0;
            }
            int discount = eligible * percent / 100;
            return maxDiscount > 0 ? Math.min(discount, maxDiscount) : discount;
        }

        public String discountText() {
            return percent + "%";
        }

        public String expireText() {
            return String.format(java.util.Locale.KOREA, "%d.%02d.%02d 23:59 까지", endYear, endMonth, endDay);
        }
    }

    private static final Coupon[] ALL = {
            new Coupon("NEWBETTER_NEW_30", TYPE_BRAND, "뉴베러 신상품 쿠폰", 30, 50000, 1,
                    "1원 이상 구매 시 최대 50,000원 할인", NEW_ONLY, "신상품", 2026, 12, 30),
            new Coupon("WINTER_LONGSLEEVE_30", TYPE_ITEM, "겨울 긴소매 쿠폰", 30, 30000, 1,
                    "1원 이상 구매 시 최대 30,000원 할인", LONG_SLEEVE_TOP, "긴소매 상의", 2026, 11, 30),
            new Coupon("FALL_LONGSLEEVE_30", TYPE_ITEM, "가을 긴소매 쿠폰", 30, 30000, 1,
                    "1원 이상 구매 시 최대 30,000원 할인", LONG_SLEEVE_TOP, "긴소매 상의", 2026, 8, 18),
            new Coupon("ALL_5", TYPE_MALL, "전상품 5% 쿠폰", 5, 0, 20000,
                    "20,000원 이상 구매 시 사용", ANY, "", 2026, 8, 31),
    };

    private CheckoutCoupons() {
    }

    /** id로 쿠폰 찾기 (없으면 null) */
    public static Coupon byId(String id) {
        for (Coupon coupon : ALL) {
            if (coupon.id.equals(id)) {
                return coupon;
            }
        }
        return null;
    }

    public static List<Coupon> owned() {
        List<Coupon> result = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        for (Coupon coupon : ALL) {
            if (MyPageStore.isCouponReceived(coupon.id)
                    && !MyPageStore.isCouponUsed(coupon.id)
                    && !coupon.isExpired(now)) {
                result.add(coupon);
            }
        }
        return result;
    }

    public static List<Coupon> finished() {
        List<Coupon> result = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        for (Coupon coupon : ALL) {
            if (MyPageStore.isCouponReceived(coupon.id)
                    && (MyPageStore.isCouponUsed(coupon.id) || coupon.isExpired(now))) {
                result.add(coupon);
            }
        }
        return result;
    }
}
