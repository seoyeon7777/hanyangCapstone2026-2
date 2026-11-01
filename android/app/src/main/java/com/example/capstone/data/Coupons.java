package com.example.capstone.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class Coupons {

    public static final class Coupon {
        public final String id;
        public final String discountLabel;
        public final String name;
        public final String condition;
        public final int percent;
        public final int amount;
        public final int maxDiscount;
        public final int minOrder;
        public final int expireYear;
        public final int expireMonth;
        public final int expireDay;

        Coupon(String id, String name, int percent, int amount, int maxDiscount, int minOrder,
               int expireYear, int expireMonth, int expireDay) {
            this.id = id;
            this.name = name;
            this.percent = percent;
            this.amount = amount;
            this.maxDiscount = maxDiscount;
            this.minOrder = minOrder;
            this.expireYear = expireYear;
            this.expireMonth = expireMonth;
            this.expireDay = expireDay;
            this.discountLabel = percent > 0 ? percent + "%" : MyPageStore.formatPrice(amount);
            this.condition = buildCondition();
        }

        private String buildCondition() {
            String min = minOrder <= 1 ? "1원 이상 구매 시" : MyPageStore.formatPrice(minOrder) + " 이상 구매 시";
            if (percent > 0 && maxDiscount > 0) {
                return min + " 최대 " + MyPageStore.formatPrice(maxDiscount) + " 할인";
            }
            return min + " 사용";
        }

        public String expireText() {
            return String.format(Locale.ROOT, "%04d.%02d.%02d 23:59 까지", expireYear, expireMonth, expireDay);
        }

        public boolean isExpired() {
            Calendar end = Calendar.getInstance();
            end.clear();
            end.set(expireYear, expireMonth - 1, expireDay, 23, 59, 59);
            return Calendar.getInstance().after(end);
        }

        public int discountFor(int orderPrice) {
            if (orderPrice < minOrder || isExpired()) {
                return 0;
            }
            int discount = percent > 0 ? orderPrice * percent / 100 : amount;
            if (maxDiscount > 0) {
                discount = Math.min(discount, maxDiscount);
            }
            return Math.min(discount, orderPrice);
        }
    }

    public static final Coupon[] AVAILABLE = {
            new Coupon("NEWBETTER_NEW_30", "뉴베러 신상품 쿠폰", 30, 0, 50000, 1, 2026, 12, 31),
            new Coupon("FALL_LONGSLEEVE_30", "가을 긴소매 쿠폰", 30, 0, 30000, 1, 2026, 12, 31),
            new Coupon("ALL_5", "전상품 5% 쿠폰", 5, 0, 0, 20000, 2026, 12, 31),
    };

    public static final Coupon[] CODE_COUPONS = {
            new Coupon("WELCOME5000", "웰컴 쿠폰", 0, 5000, 0, 10000, 2026, 12, 31),
    };

    private Coupons() {
    }

    public static Coupon find(String id) {
        for (Coupon c : AVAILABLE) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        for (Coupon c : CODE_COUPONS) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public static Coupon findByCode(String code) {
        for (Coupon c : CODE_COUPONS) {
            if (c.id.equalsIgnoreCase(code)) {
                return c;
            }
        }
        return null;
    }

    public static List<Coupon> usable(int orderPrice) {
        List<Coupon> result = new ArrayList<>();
        for (String id : MyPageStore.getReceivedCouponIds()) {
            Coupon c = find(id);
            if (c != null && !MyPageStore.isCouponUsed(id) && c.discountFor(orderPrice) > 0) {
                result.add(c);
            }
        }
        return result;
    }
}
