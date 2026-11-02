package com.example.capstone.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// [여기에 DB 입력] 보유 쿠폰 조회·사용 처리 (user_coupons, coupons)
public final class CheckoutCoupons {

    public static final class Coupon {
        public final String id;
        public final String name;
        public final int percent;
        public final int maxDiscount;
        public final int minOrder;
        public final String conditionText;
        private final int endYear, endMonth, endDay;

        Coupon(String id, String name, int percent, int maxDiscount, int minOrder,
               String conditionText, int endYear, int endMonth, int endDay) {
            this.id = id;
            this.name = name;
            this.conditionText = conditionText;
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

        public int discountFor(int orderPrice) {
            if (orderPrice < minOrder) {
                return 0;
            }
            int discount = orderPrice * percent / 100;
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
            new Coupon("NEWBETTER_NEW_30", "뉴베러 신상품 쿠폰", 30, 50000, 1,
                    "1원 이상 구매 시 최대 50,000원 할인", 2026, 12, 30),
            new Coupon("WINTER_LONGSLEEVE_30", "겨울 긴소매 쿠폰", 30, 30000, 1,
                    "1원 이상 구매 시 최대 30,000원 할인", 2026, 11, 30),
            new Coupon("FALL_LONGSLEEVE_30", "가을 긴소매 쿠폰", 30, 30000, 1,
                    "1원 이상 구매 시 최대 30,000원 할인", 2026, 8, 18),
            new Coupon("ALL_5", "전상품 5% 쿠폰", 5, 0, 20000,
                    "20,000원 이상 구매 시 사용", 2026, 8, 31),
    };

    private CheckoutCoupons() {
    }

    public static List<Coupon> owned() {
        List<Coupon> result = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        for (Coupon coupon : ALL) {
            if (MyPageStore.isCouponReceived(coupon.id) && !coupon.isExpired(now)) {
                result.add(coupon);
            }
        }
        return result;
    }
}
