package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.capstone.BuildConfig;
import com.example.capstone.login.LoginSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class DemoData {

    static final String PREF_NAME = "demo_data";
    static final String KEY_SEEDED = "seeded@";
    static final String KEY_PRODUCT_REVIEWS = "product_reviews_seeded";
    static final String REVIEW_PREF = "capstone_reviews";
    static final String REVIEW_KEY = "reviews_product_";

    private DemoData() {
    }

    public static void seedIfNeeded(Context context) {
        if (!BuildConfig.DEBUG || !LoginSession.isLoggedIn(context)) {
            return;
        }
        seed(context, BodyProfileStore.accountKey(context), Calendar.getInstance());
    }

    static void seed(Context context, String account, Calendar now) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SEEDED + account, false)) {
            seedAccount(now);
            prefs.edit().putBoolean(KEY_SEEDED + account, true).apply();
        }
        if (!prefs.getBoolean(KEY_PRODUCT_REVIEWS, false)) {
            seedProductReviews(context, now);
            prefs.edit().putBoolean(KEY_PRODUCT_REVIEWS, true).apply();
        }
    }

    private static void seedAccount(Calendar now) {
        long base = now.getTimeInMillis();

        MyPageStore.Order reviewed = order(base - 7, daysAgo(now, 20), 2, "M / WHITE",
                MyPageStore.STATUS_DONE, "CJ대한통운", "651234567801");
        MyPageStore.Order delivered = order(base - 6, daysAgo(now, 14), 3, "M / BLUE",
                MyPageStore.STATUS_DONE, "CJ대한통운", "651234567802");
        MyPageStore.Order returning = order(base - 5, daysAgo(now, 12), 4, "L / GRAY",
                MyPageStore.STATUS_DONE, "롯데택배", "230987654301");
        MyPageStore.Order canceled = order(base - 4, daysAgo(now, 6), 1, "M / BLACK",
                MyPageStore.STATUS_PAID, "", "");
        MyPageStore.Order shipping = order(base - 3, daysAgo(now, 3), 5, "M / NAVY",
                MyPageStore.STATUS_SHIPPING, "한진택배", "512345678903");
        MyPageStore.Order ready = order(base - 2, daysAgo(now, 2), 6, "S / BLACK",
                MyPageStore.STATUS_READY, "", "");
        MyPageStore.Order paid = order(base - 1, daysAgo(now, 0), 7, "M / WHITE",
                MyPageStore.STATUS_PAID, "", "");

        for (MyPageStore.Order order : new MyPageStore.Order[]{
                reviewed, delivered, returning, canceled, shipping, ready, paid}) {
            MyPageStore.addOrder(order);
        }

        MyPageStore.addReview(reviewed, 5f, "핏이 깔끔하고 소재가 좋아요. 다른 색도 사고 싶어요.");
        MyPageStore.requestClaim(returning, MyPageStore.CLAIM_RETURN, "사이즈가 맞지 않음");
        MyPageStore.requestClaim(canceled, MyPageStore.CLAIM_CANCEL, "단순 변심");

        MyPageStore.Inquiry answered = new MyPageStore.Inquiry(
                "배송",
                "언제 출발하나요?",
                "주문한 니트가 언제 출발하는지 궁금해요.",
                MyPageStore.INQUIRY_ANSWERED,
                daysAgo(now, 4)
        );
        answered.answer = "안녕하세요, 고객님. 주문하신 상품은 결제 후 2~3일 안에 출발해요. "
                + "출발하면 주문 조회의 배송조회에서 송장번호를 확인할 수 있어요.";
        MyPageStore.addInquiry(answered);

        MyPageStore.addInquiry(new MyPageStore.Inquiry(
                "취소/반품/교환",
                "사이즈 교환 문의",
                "받은 후드 집업이 커서 M으로 바꾸고 싶어요.",
                MyPageStore.INQUIRY_WAITING,
                daysAgo(now, 1)
        ));
    }

    private static MyPageStore.Order order(long no, String date, int productId, String option,
                                           int status, String courier, String trackingNo) {
        Product product = DummyData.getProductById(productId);
        String brand = product == null ? "" : product.getBrand();
        String name = product == null ? "" : product.getName();
        int price = product == null ? 0 : product.getPrice();
        return new MyPageStore.Order(
                "ORD" + no,
                date,
                brand,
                name,
                option + " / 총 1개",
                1,
                price,
                status,
                courier,
                trackingNo
        );
    }

    private static void seedProductReviews(Context context, Calendar now) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(REVIEW_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        put(prefs, editor, 1, new Object[][]{
                {5, "여름에 딱이에요", "통이 넉넉해서 편하고 세탁해도 늘어나지 않아요.", 3},
                {4, "무난해요", "기본 티로 입기 좋아요. 살짝 비치는 편이에요.", 9},
                {2, "생각보다 커요", "오버핏이라 한 사이즈 작게 사는 걸 추천해요.", 15},
        }, now);
        put(prefs, editor, 2, new Object[][]{
                {5, "출근룩으로 좋아요", "구김이 적고 핏이 깔끔해요.", 2},
                {5, "재구매했어요", "화이트 사고 블루도 샀어요.", 8},
                {3, "보통이에요", "소매가 조금 길어요.", 20},
        }, now);
        put(prefs, editor, 3, new Object[][]{
                {4, "다리가 길어 보여요", "와이드라 체형을 잘 가려 줘요.", 5},
                {1, "색이 달라요", "사진보다 연한 색이 왔어요.", 11},
        }, now);
        put(prefs, editor, 5, new Object[][]{
                {5, "부드러워요", "따갑지 않고 따뜻해요.", 1},
        }, now);

        editor.apply();
    }

    private static void put(SharedPreferences prefs, SharedPreferences.Editor editor,
                            int productId, Object[][] rows, Calendar now) {
        String key = REVIEW_KEY + productId;
        if (prefs.contains(key)) {
            return;
        }
        try {
            JSONArray array = new JSONArray();
            for (Object[] row : rows) {
                JSONObject review = new JSONObject();
                review.put("rating", ((Integer) row[0]).doubleValue());
                review.put("title", row[1]);
                review.put("content", row[2]);
                review.put("date", daysAgo(now, (Integer) row[3]));
                array.put(review);
            }
            editor.putString(key, array.toString());
        } catch (Exception ignored) {
        }
    }

    private static String daysAgo(Calendar now, int days) {
        Calendar day = (Calendar) now.clone();
        day.add(Calendar.DAY_OF_MONTH, -days);
        return new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(day.getTime());
    }
}
