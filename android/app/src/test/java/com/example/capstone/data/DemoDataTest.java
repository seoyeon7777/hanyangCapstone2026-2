package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.capstone.login.LoginSession;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class DemoDataTest {

    private Context context;
    private String account;

    @Before
    public void setUp() {
        context = new CartStoreTest.FakeContext();
        account = login();
    }

    private String login() {
        String email = UUID.randomUUID() + "@test.com";
        LoginSession.updateProfile(context, "테스트", email, "");
        MyPageStore.init(context);
        return BodyProfileStore.accountKey(context);
    }

    private void seed() {
        DemoData.seed(context, account, Calendar.getInstance());
    }

    @Test
    public void ordersCoverEveryStatus() {
        seed();
        assertEquals(7, MyPageStore.countOrders(MyPageStore.STATUS_ALL));
        assertEquals(1, MyPageStore.countOrders(MyPageStore.STATUS_PAID));
        assertEquals(1, MyPageStore.countOrders(MyPageStore.STATUS_READY));
        assertEquals(1, MyPageStore.countOrders(MyPageStore.STATUS_SHIPPING));
        assertEquals(2, MyPageStore.countOrders(MyPageStore.STATUS_DONE));
        assertEquals(1, MyPageStore.countOrders(MyPageStore.STATUS_CANCELED));
        assertEquals(1, MyPageStore.countOrders(MyPageStore.STATUS_RETURNING));
    }

    @Test
    public void ordersUseRealProductNamesAndPrices() {
        seed();
        for (MyPageStore.Order order : MyPageStore.getOrders(MyPageStore.STATUS_ALL)) {
            assertFalse(order.productName.isEmpty());
            assertTrue(order.price > 0);
        }
    }

    @Test
    public void buttonsMatchStatus() {
        seed();
        for (MyPageStore.Order order : MyPageStore.getOrders(MyPageStore.STATUS_ALL)) {
            boolean cancel = MyPageStore.canCancel(order);
            boolean ret = MyPageStore.canReturnOrExchange(order);
            assertEquals(order.status == MyPageStore.STATUS_PAID, cancel);
            assertEquals(order.status == MyPageStore.STATUS_DONE, ret);
        }
    }

    @Test
    public void shippingOrderHasTrackingButReadyDoesNot() {
        seed();
        MyPageStore.Order shipping = MyPageStore.getOrders(MyPageStore.STATUS_SHIPPING).get(0);
        MyPageStore.Order ready = MyPageStore.getOrders(MyPageStore.STATUS_READY).get(0);
        assertFalse(shipping.trackingNo.isEmpty());
        assertFalse(shipping.courier.isEmpty());
        assertTrue(ready.trackingNo.isEmpty());
    }

    @Test
    public void oneReviewWrittenAndOneWritable() {
        seed();
        assertEquals(1, MyPageStore.getMyReviews().size());
        assertEquals(5f, MyPageStore.getMyReviews().get(0).rating, 0.001f);
        assertEquals(1, MyPageStore.getWritableReviewOrders().size());
    }

    @Test
    public void writingReviewRemovesItFromWritable() {
        seed();
        MyPageStore.Order order = MyPageStore.getWritableReviewOrders().get(0);
        MyPageStore.addReview(order, 3f, "보통");
        assertEquals(0, MyPageStore.getWritableReviewOrders().size());
        assertEquals(2, MyPageStore.getMyReviews().size());
        MyPageStore.addReview(order, 1f, "두 번");
        assertEquals(2, MyPageStore.getMyReviews().size());
    }

    @Test
    public void claimsAreCreated() {
        seed();
        List<MyPageStore.Claim> cancels = MyPageStore.getClaims(MyPageStore.CLAIM_CANCEL);
        List<MyPageStore.Claim> returns = MyPageStore.getClaims(MyPageStore.CLAIM_RETURN);
        assertEquals(1, cancels.size());
        assertEquals(MyPageStore.CLAIM_STATE_DONE, cancels.get(0).state);
        assertEquals(1, returns.size());
        assertEquals(MyPageStore.CLAIM_STATE_PROCESSING, returns.get(0).state);
    }

    @Test
    public void withdrawingReturnPutsOrderBackToDelivered() {
        seed();
        MyPageStore.Claim ret = MyPageStore.getClaims(MyPageStore.CLAIM_RETURN).get(0);
        MyPageStore.withdrawClaim(ret);
        assertEquals(MyPageStore.CLAIM_STATE_WITHDRAWN, ret.state);
        assertEquals(MyPageStore.STATUS_DONE, MyPageStore.findOrder(ret.orderNo).status);
        assertEquals(3, MyPageStore.countOrders(MyPageStore.STATUS_DONE));
    }

    @Test
    public void cancelingPaidOrderMovesItToCanceled() {
        seed();
        MyPageStore.Order paid = MyPageStore.getOrders(MyPageStore.STATUS_PAID).get(0);
        MyPageStore.requestClaim(paid, MyPageStore.CLAIM_CANCEL, "기타");
        assertEquals(MyPageStore.STATUS_CANCELED, paid.status);
        assertFalse(MyPageStore.canCancel(paid));
        assertEquals(2, MyPageStore.getClaims(MyPageStore.CLAIM_CANCEL).size());
    }

    @Test
    public void inquiryAnswerSurvivesReload() {
        seed();
        String first = account;
        login();
        assertEquals(0, MyPageStore.getInquiries().size());

        LoginSession.updateProfile(context, "테스트", first, "");
        MyPageStore.init(context);
        List<MyPageStore.Inquiry> inquiries = MyPageStore.getInquiries();
        assertEquals(2, inquiries.size());
        int answered = 0;
        for (MyPageStore.Inquiry inquiry : inquiries) {
            if (MyPageStore.INQUIRY_ANSWERED.equals(inquiry.status)) {
                answered++;
                assertFalse(inquiry.answer.isEmpty());
            } else {
                assertTrue(inquiry.answer.isEmpty());
            }
        }
        assertEquals(1, answered);
        assertEquals(7, MyPageStore.countOrders(MyPageStore.STATUS_ALL));
    }

    @Test
    public void seedingTwiceDoesNotDuplicate() {
        seed();
        seed();
        assertEquals(7, MyPageStore.countOrders(MyPageStore.STATUS_ALL));
        assertEquals(2, MyPageStore.getInquiries().size());
    }

    @Test
    public void eachAccountGetsItsOwnCopy() {
        seed();
        MyPageStore.Order paid = MyPageStore.getOrders(MyPageStore.STATUS_PAID).get(0);
        MyPageStore.requestClaim(paid, MyPageStore.CLAIM_CANCEL, "기타");

        account = login();
        seed();
        assertEquals(7, MyPageStore.countOrders(MyPageStore.STATUS_ALL));
        assertEquals(1, MyPageStore.countOrders(MyPageStore.STATUS_PAID));
    }

    @Test
    public void productReviewsAreWrittenOnce() throws Exception {
        SharedPreferences reviews = context.getSharedPreferences(DemoData.REVIEW_PREF, 0);
        reviews.edit().putString(DemoData.REVIEW_KEY + 2, "[]").apply();
        seed();

        JSONArray first = new JSONArray(reviews.getString(DemoData.REVIEW_KEY + 1, "[]"));
        assertEquals(3, first.length());
        double sum = 0;
        for (int i = 0; i < first.length(); i++) {
            sum += first.getJSONObject(i).getDouble("rating");
        }
        assertEquals(11.0 / 3, sum / first.length(), 0.001);

        assertEquals("[]", reviews.getString(DemoData.REVIEW_KEY + 2, ""));
        assertEquals(2, new JSONArray(reviews.getString(DemoData.REVIEW_KEY + 3, "[]")).length());
        assertEquals(1, new JSONArray(reviews.getString(DemoData.REVIEW_KEY + 5, "[]")).length());
        assertFalse(reviews.contains(DemoData.REVIEW_KEY + 4));
    }
}
