package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** 브랜드·상품 쿠폰은 이름에 맞는 상품에만 적용된다 (전상품 쿠폰은 어디든) */
public class CheckoutCouponsTest {

    private static CheckoutCoupons.Line line(String name, String category, boolean isNew, int price) {
        return new CheckoutCoupons.Line(name, category, isNew, price);
    }

    @Test
    public void newProductCouponAppliesOnlyToNewProducts() {
        CheckoutCoupons.Coupon coupon = CheckoutCoupons.byId("NEWBETTER_NEW_30");
        assertNotNull(coupon);

        List<CheckoutCoupons.Line> oldOnly = Arrays.asList(line("카고 와이드 팬츠", "바지", false, 39000));
        assertFalse(coupon.appliesToAny(oldOnly));
        assertEquals(0, coupon.discountFor(oldOnly));

        List<CheckoutCoupons.Line> withNew = Arrays.asList(
                line("카고 와이드 팬츠", "바지", false, 39000),
                line("A라인 롱 스커트", "스커트", true, 41000));
        assertTrue(coupon.appliesToAny(withNew));
        assertEquals(41000 * 30 / 100, coupon.discountFor(withNew));   // 신상품 금액만으로 계산
    }

    @Test
    public void longSleeveCouponAppliesToLongSleeveTopsOnly() {
        CheckoutCoupons.Coupon coupon = CheckoutCoupons.byId("WINTER_LONGSLEEVE_30");
        assertNotNull(coupon);

        assertEquals(0, coupon.discountFor(Arrays.asList(line("카고 와이드 팬츠", "바지", false, 39000))));
        assertEquals(0, coupon.discountFor(Arrays.asList(line("오버핏 반팔 티셔츠", "상의", false, 10000))));
        assertEquals(0, coupon.discountFor(Arrays.asList(line("스트라이프 민소매", "상의", false, 12000))));
        assertEquals(19000 * 30 / 100, coupon.discountFor(Arrays.asList(line("베이직 셔츠", "상의", false, 19000))));
        assertEquals(30000, coupon.discountFor(Arrays.asList(line("두꺼운 캐주얼 니트", "상의", false, 200000))));   // 최대 3만원
    }

    @Test
    public void mallCouponAppliesToEverythingAndKeepsMinimumOrder() {
        CheckoutCoupons.Coupon coupon = CheckoutCoupons.byId("ALL_5");
        assertNotNull(coupon);
        assertEquals(0, coupon.discountFor(Arrays.asList(line("양말", "기타", false, 19000))));        // 2만원 미만
        assertEquals(2000, coupon.discountFor(Arrays.asList(line("카고 와이드 팬츠", "바지", false, 40000))));
    }
}
