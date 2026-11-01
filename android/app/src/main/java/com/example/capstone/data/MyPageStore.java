package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.capstone.login.LoginSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MyPageStore {

    // 주문 상태
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_READY = 2;
    public static final int STATUS_SHIPPING = 3;
    public static final int STATUS_DONE = 4;
    public static final int STATUS_CANCELED = 5;
    public static final int STATUS_RETURNING = 6;
    public static final int STATUS_EXCHANGING = 7;
    public static final int STATUS_ALL = -1;

    public static final String[] STATUS_LABELS = {
            "주문접수", "결제완료", "배송준비중", "배송중",
            "배송완료", "취소완료", "반품신청", "교환신청"
    };

    public static final long STATUS_STEP_MILLIS = 60 * 1000L;
    public static final long INQUIRY_ANSWER_MILLIS = 60 * 1000L;
    public static final String COURIER = "CJ대한통운";

    public static final String CLAIM_CANCEL = "취소";
    public static final String CLAIM_RETURN = "반품";
    public static final String CLAIM_EXCHANGE = "교환";

    public static final String CLAIM_STATE_PROCESSING = "처리중";
    public static final String CLAIM_STATE_DONE = "처리완료";
    public static final String CLAIM_STATE_WITHDRAWN = "철회";

    public static final String INQUIRY_WAITING = "답변대기";
    public static final String INQUIRY_ANSWERED = "답변완료";

    // 회원
    public static class User {
        public String name;
        public String email;
        public String phone;
        public String password;
        public String nickname = "";

        User(String name, String email, String phone, String password) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.password = password;
        }
    }

    // 주문
    public static class Order {
        public final String orderNo;
        public final String orderDate;
        public final String brand;
        public final String productName;
        public final String option;
        public final int quantity;
        public final int price;

        public int status;
        public boolean reviewed;

        public String courier;
        public String trackingNo;

        public String groupNo = "";
        public int productId;
        public String serverId = "";
        public String size = "";
        public String color = "";
        public String receiver = "";
        public String receiverPhone = "";
        public String zipCode = "";
        public String address = "";
        public String request = "";
        public String paymentMethod = "";
        public int discount;
        public int shippingFee;
        public long createdAt;

        public Order(
                String orderNo,
                String orderDate,
                String brand,
                String productName,
                String option,
                int quantity,
                int price,
                int status,
                String courier,
                String trackingNo
        ) {
            this.orderNo = orderNo;
            this.orderDate = orderDate;
            this.brand = brand;
            this.productName = productName;
            this.option = option;
            this.quantity = quantity;
            this.price = price;
            this.status = status;
            this.courier = courier;
            this.trackingNo = trackingNo;
            this.createdAt = System.currentTimeMillis();
        }

        public String getStatusLabel() {
            if (status < 0 || status >= STATUS_LABELS.length) {
                return "상태 확인 중";
            }
            return STATUS_LABELS[status];
        }

        public int getTotalPrice() {
            return price * quantity;
        }

        public int getPaidPrice() {
            return Math.max(0, getTotalPrice() - discount);
        }

        public String getShippingAddress() {
            if (address.isEmpty()) {
                return "";
            }
            String zip = zipCode.isEmpty() ? "" : "(" + zipCode + ") ";
            return zip + address;
        }
    }

    // 취소 / 반품 / 교환
    public static class Claim {
        public final String claimNo;
        public final String type;
        public final String orderNo;
        public final String productName;
        public final String option;
        public final int price;
        public final String requestDate;
        public final String reason;
        public String state;

        public Claim(
                String claimNo,
                String type,
                String orderNo,
                String productName,
                String option,
                int price,
                String requestDate,
                String reason,
                String state
        ) {
            this.claimNo = claimNo;
            this.type = type;
            this.orderNo = orderNo;
            this.productName = productName;
            this.option = option;
            this.price = price;
            this.requestDate = requestDate;
            this.reason = reason;
            this.state = state;
        }
    }

    // 배송지
    public static class Address {
        public final long id;
        public String label;
        public String receiver;
        public String phone;
        public String zipCode;
        public String address1;
        public String address2;
        public String request;
        public boolean isDefault;

        public Address(
                long id,
                String label,
                String receiver,
                String phone,
                String zipCode,
                String address1,
                String address2,
                String request,
                boolean isDefault
        ) {
            this.id = id;
            this.label = label;
            this.receiver = receiver;
            this.phone = phone;
            this.zipCode = zipCode;
            this.address1 = address1;
            this.address2 = address2;
            this.request = request;
            this.isDefault = isDefault;
        }

        public String getFullAddress() {
            return "(" + zipCode + ") " + address1 + " " + address2;
        }
    }

    // 결제 카드
    public static class PaymentCard {
        public final long id;
        public final String company;
        public final String lastFour;
        public final String expiry;
        public boolean isDefault;

        public PaymentCard(
                long id,
                String company,
                String lastFour,
                String expiry,
                boolean isDefault
        ) {
            this.id = id;
            this.company = company;
            this.lastFour = lastFour;
            this.expiry = expiry;
            this.isDefault = isDefault;
        }

        public String getMaskedNumber() {
            return "**** **** **** " + lastFour;
        }
    }

    // 환불 계좌
    public static class RefundAccount {
        public final String bank;
        public final String accountNo;
        public final String holder;
        public boolean isDefault;

        public RefundAccount(
                String bank,
                String accountNo,
                String holder
        ) {
            this.bank = bank;
            this.accountNo = accountNo;
            this.holder = holder;
        }

        public String getMaskedAccountNo() {
            if (accountNo.length() <= 8) {
                return accountNo;
            }

            StringBuilder sb = new StringBuilder(
                    accountNo.substring(0, 4)
            );

            for (int i = 4; i < accountNo.length() - 4; i++) {
                sb.append('*');
            }

            sb.append(accountNo.substring(accountNo.length() - 4));
            return sb.toString();
        }
    }

    // 리뷰
    public static class MyReview {
        public final long id;
        public final String orderNo;
        public final String productName;
        public final String option;
        public float rating;
        public String content;
        public String writtenDate;

        public String accountId = "";
        public String author = "";
        public int productId;
        public String serverId = "";

        public MyReview(
                long id,
                String orderNo,
                String productName,
                String option,
                float rating,
                String content,
                String writtenDate
        ) {
            this.id = id;
            this.orderNo = orderNo;
            this.productName = productName;
            this.option = option;
            this.rating = rating;
            this.content = content;
            this.writtenDate = writtenDate;
        }
    }

    // 문의
    public static class Inquiry {
        public final String type;
        public final String title;
        public final String content;
        public final String email;
        public String status;
        public final String date;

        public long createdAt;
        public String answer = "";

        public Inquiry(
                String type,
                String title,
                String content,
                String email,
                String status,
                String date
        ) {
            this.type = type;
            this.title = title;
            this.content = content;
            this.email = email;
            this.status = status;
            this.date = date;
            this.createdAt = System.currentTimeMillis();
        }
    }

    // 공지사항 / FAQ
    public static class Notice {
        public final String category;
        public final String title;
        public final String date;
        public final String content;

        public Notice(
                String category,
                String title,
                String date,
                String content
        ) {
            this.category = category;
            this.title = title;
            this.date = date;
            this.content = content;
        }
    }

    // 데이터 목록
    private static User user = new User("", "", "", "");

    private static final List<Order> orders = new ArrayList<>();
    private static final List<Claim> claims = new ArrayList<>();
    private static final List<Address> addresses = new ArrayList<>();
    private static final List<PaymentCard> cards = new ArrayList<>();
    private static final List<MyReview> reviews = new ArrayList<>();
    private static final List<Notice> notices = new ArrayList<>();
    private static final List<Inquiry> inquiries = new ArrayList<>();

    private static final Set<String> receivedCouponIds = new LinkedHashSet<>();
    private static final Set<String> usedCouponIds = new HashSet<>();

    private static final List<RefundAccount> refundAccounts = new ArrayList<>();
    private static boolean kakaoPayLinked = false;
    private static boolean naverPayLinked = false;

    private static long nextId = 1;

    // FAQ 기본 데이터
    private static final List<Notice> faqs = new ArrayList<>(
            Arrays.asList(
                    faq(
                            "가상피팅",
                            "가상피팅은 어떻게 이용하나요?",
                            "하단 「피팅」 메뉴에서 키·몸무게와 정면·옆면 전신 사진을 고르면 가슴·허리·엉덩이 둘레를 계산해 줍니다. 저장한 치수로 상품마다 맞는 사이즈를 알려 드립니다."
                    ),
                    faq(
                            "가상피팅",
                            "측정한 치수가 실제와 달라요.",
                            "몸에 붙는 옷을 입고 다시 찍은 사진으로 측정해 보세요. 사진으로 구하지 못한 값은 키·몸무게·나이로 어림한 값이며 (추정)으로 표시됩니다."
                    ),
                    faq(
                            "가상피팅",
                            "전신 사진은 어떻게 찍어야 정확한가요?",
                            "몸에 붙는 옷을 입고, 밝고 단순한 배경 앞에서 정면 전신이 모두 나오도록 찍어주세요. 카메라는 허리 높이에서 수평으로 들면 가장 정확합니다."
                    ),
                    faq(
                            "가상피팅",
                            "고른 사진은 저장되나요?",
                            "사진은 기기 안에서 신체 치수 계산에만 쓰이며 서버로 보내지 않습니다."
                    ),
                    faq(
                            "가상피팅",
                            "가상피팅 결과와 실제 착용감이 다를 수 있나요?",
                            "옷의 소재, 신축성, 개인 체형 차이에 따라 실제 착용감과 차이가 있을 수 있습니다. 상품 상세의 실측 사이즈도 함께 확인해주세요."
                    ),
                    faq(
                            "주문/결제",
                            "어떤 결제 수단을 쓸 수 있나요?",
                            "신용/체크카드, 카카오페이, 네이버페이로 결제할 수 있습니다. 결제 수단은 마이페이지 > 결제 수단에서 관리할 수 있습니다."
                    ),
                    faq(
                            "주문/결제",
                            "쿠폰은 어떻게 받고 사용하나요?",
                            "마이페이지 > 쿠폰함 > 쿠폰 받기에서 쿠폰을 받을 수 있으며, 결제할 때 받은 쿠폰을 적용할 수 있습니다."
                    ),
                    faq(
                            "배송",
                            "배송은 얼마나 걸리나요?",
                            "결제 완료 후 영업일 기준 2~3일 이내에 받아보실 수 있습니다. 도서산간 지역은 추가로 시간이 걸릴 수 있습니다."
                    ),
                    faq(
                            "배송",
                            "배송지를 바꾸고 싶어요.",
                            "배송준비중 전까지는 마이페이지 > 배송지 관리에서 배송지를 변경할 수 있습니다."
                    ),
                    faq(
                            "취소/반품/교환",
                            "주문을 취소하고 싶어요.",
                            "결제완료 상태까지는 주문내역에서 상품별로 바로 취소할 수 있습니다. 배송준비중부터는 1:1 문의로 요청해주세요."
                    ),
                    faq(
                            "취소/반품/교환",
                            "반품·교환은 언제까지 가능한가요?",
                            "상품을 받은 날로부터 7일 이내에 신청할 수 있습니다. 착용, 세탁했거나 택을 제거한 상품은 반품·교환이 어렵습니다."
                    ),
                    faq(
                            "취소/반품/교환",
                            "환불은 언제 되나요?",
                            "반품 상품이 확인된 후 영업일 기준 3~5일 이내에 환불됩니다. 환불 계좌는 마이페이지 > 환불 계좌에서 등록할 수 있습니다."
                    ),
                    faq(
                            "회원정보",
                            "비밀번호를 바꾸고 싶어요.",
                            "마이페이지 > 프로필 수정 > 비밀번호 변경에서 바꿀 수 있습니다."
                    ),
                    faq(
                            "회원정보",
                            "계정/비밀번호를 잊어버렸어요.",
                            "로그인 화면의 계정 찾기, 비밀번호 재설정에서 찾을 수 있습니다."
                    ),
                    faq(
                            "회원정보",
                            "회원 탈퇴는 어떻게 하나요?",
                            "마이페이지 > 프로필 수정 하단의 「회원 탈퇴」에서 탈퇴할 수 있습니다. 탈퇴하면 주문 내역, 쿠폰, 리뷰 등 모든 정보가 삭제되며 복구할 수 없습니다."
                    ),
                    faq(
                            "기타",
                            "문의 답변은 언제 받을 수 있나요?",
                            "영업일 기준 1~2일 이내에 답변드립니다. 답변은 입력하신 이메일과 1:1 문의 > 문의내역에서 확인할 수 있습니다."
                    )
            )
    );

    // 주문 / 리뷰 영구 저장
    private static SharedPreferences storage;
    private static Context appContext;
    private static String accountKey = "guest";

    private static final String KEY_DATA = "data@";
    private static final String KEY_REVIEWS = "reviews_all";
    private static final String KEY_NEXT_ID = "next_id";

    public static synchronized void init(Context context) {
        if (storage != null) {
            return;
        }

        appContext = context.getApplicationContext();
        storage = appContext.getSharedPreferences(
                "capstone_order_review_store",
                Context.MODE_PRIVATE
        );

        nextId = Math.max(nextId, storage.getLong(KEY_NEXT_ID, 1));
        loadReviews();
        loadAccount(currentAccountKey());
    }

    public static synchronized void reload(Context context) {
        if (storage == null) {
            init(context);
            return;
        }
        loadAccount(currentAccountKey());
    }

    private static String currentAccountKey() {
        if (appContext == null || !LoginSession.isLoggedIn(appContext)) {
            return "guest";
        }
        String id = LoginSession.getUserId(appContext);
        return id.isEmpty() ? BodyProfileStore.accountKey(appContext) : id;
    }

    private static void clearAccountData() {
        orders.clear();
        claims.clear();
        addresses.clear();
        cards.clear();
        inquiries.clear();
        receivedCouponIds.clear();
        usedCouponIds.clear();
        refundAccounts.clear();
        kakaoPayLinked = false;
        naverPayLinked = false;
    }

    private static void loadAccount(String key) {
        accountKey = key;
        clearAccountData();

        String raw = storage.getString(KEY_DATA + key, null);
        boolean legacy = false;
        if (raw == null && !"guest".equals(key) && storage.contains("orders")) {
            legacy = true;
        }

        try {
            if (legacy) {
                JSONArray savedOrders = new JSONArray(storage.getString("orders", "[]"));
                for (int i = 0; i < savedOrders.length(); i++) {
                    orders.add(orderFromJson(savedOrders.getJSONObject(i)));
                }
                JSONArray savedReviews = new JSONArray(storage.getString("reviews", "[]"));
                for (int i = 0; i < savedReviews.length(); i++) {
                    MyReview review = reviewFromJson(savedReviews.getJSONObject(i));
                    review.accountId = key;
                    if (findReview(review.id) == null) {
                        reviews.add(review);
                    }
                }
                storage.edit().remove("orders").remove("reviews").apply();
                saveReviews();
                save();
                return;
            }

            if (raw == null) {
                return;
            }

            JSONObject data = new JSONObject(raw);

            JSONArray a = data.optJSONArray("orders");
            for (int i = 0; a != null && i < a.length(); i++) {
                orders.add(orderFromJson(a.getJSONObject(i)));
            }

            a = data.optJSONArray("claims");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                claims.add(new Claim(
                        j.optString("no"),
                        j.optString("type"),
                        j.optString("orderNo"),
                        j.optString("name"),
                        j.optString("option"),
                        j.optInt("price"),
                        j.optString("date"),
                        j.optString("reason"),
                        j.optString("state")
                ));
            }

            a = data.optJSONArray("addresses");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                long id = j.optLong("id");
                addresses.add(new Address(
                        id,
                        j.optString("label"),
                        j.optString("receiver"),
                        j.optString("phone"),
                        j.optString("zip"),
                        j.optString("address1"),
                        j.optString("address2"),
                        j.optString("request"),
                        j.optBoolean("default")
                ));
                nextId = Math.max(nextId, id + 1);
            }

            a = data.optJSONArray("cards");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                long id = j.optLong("id");
                cards.add(new PaymentCard(
                        id,
                        j.optString("company"),
                        j.optString("lastFour"),
                        j.optString("expiry"),
                        j.optBoolean("default")
                ));
                nextId = Math.max(nextId, id + 1);
            }

            a = data.optJSONArray("refundAccounts");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                RefundAccount account = new RefundAccount(
                        j.optString("bank"),
                        j.optString("accountNo"),
                        j.optString("holder")
                );
                account.isDefault = j.optBoolean("default");
                refundAccounts.add(account);
            }

            a = data.optJSONArray("inquiries");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                Inquiry inquiry = new Inquiry(
                        j.optString("type"),
                        j.optString("title"),
                        j.optString("content"),
                        j.optString("email"),
                        j.optString("status", INQUIRY_WAITING),
                        j.optString("date")
                );
                inquiry.createdAt = j.optLong("createdAt", inquiry.createdAt);
                inquiry.answer = j.optString("answer");
                inquiries.add(inquiry);
            }

            a = data.optJSONArray("coupons");
            for (int i = 0; a != null && i < a.length(); i++) {
                receivedCouponIds.add(a.getString(i));
            }

            a = data.optJSONArray("usedCoupons");
            for (int i = 0; a != null && i < a.length(); i++) {
                usedCouponIds.add(a.getString(i));
            }

            kakaoPayLinked = data.optBoolean("kakaoPay");
            naverPayLinked = data.optBoolean("naverPay");

        } catch (Exception e) {
            Log.e("MyPageStore", "주문/리뷰 불러오기 실패", e);
        }
    }

    private static void loadReviews() {
        reviews.clear();
        try {
            JSONArray saved = new JSONArray(storage.getString(KEY_REVIEWS, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                MyReview review = reviewFromJson(saved.getJSONObject(i));
                reviews.add(review);
                nextId = Math.max(nextId, review.id + 1);
            }
        } catch (Exception e) {
            Log.e("MyPageStore", "주문/리뷰 불러오기 실패", e);
        }
    }

    private static Order orderFromJson(JSONObject j) {
        Order order = new Order(
                j.optString("no"),
                j.optString("date"),
                j.optString("brand"),
                j.optString("name"),
                j.optString("option"),
                j.optInt("quantity", 1),
                j.optInt("price"),
                j.optInt("status", STATUS_PAID),
                j.optString("courier"),
                j.optString("tracking")
        );

        order.reviewed = j.optBoolean("reviewed", false);
        order.groupNo = j.optString("groupNo", order.orderNo);
        order.productId = j.optInt("productId");
        order.serverId = j.optString("serverId");
        order.size = j.optString("size");
        order.color = j.optString("color");
        order.receiver = j.optString("receiver");
        order.receiverPhone = j.optString("receiverPhone");
        order.zipCode = j.optString("zip");
        order.address = j.optString("address");
        order.request = j.optString("request");
        order.paymentMethod = j.optString("payment");
        order.discount = j.optInt("discount");
        order.shippingFee = j.optInt("shippingFee");
        order.createdAt = j.optLong("createdAt", 0);
        return order;
    }

    private static JSONObject orderToJson(Order order) throws Exception {
        JSONObject j = new JSONObject();

        j.put("no", order.orderNo);
        j.put("date", order.orderDate);
        j.put("brand", order.brand);
        j.put("name", order.productName);
        j.put("option", order.option);
        j.put("quantity", order.quantity);
        j.put("price", order.price);
        j.put("status", order.status);
        j.put("reviewed", order.reviewed);
        j.put("courier", order.courier);
        j.put("tracking", order.trackingNo);
        j.put("groupNo", order.groupNo);
        j.put("productId", order.productId);
        j.put("serverId", order.serverId);
        j.put("size", order.size);
        j.put("color", order.color);
        j.put("receiver", order.receiver);
        j.put("receiverPhone", order.receiverPhone);
        j.put("zip", order.zipCode);
        j.put("address", order.address);
        j.put("request", order.request);
        j.put("payment", order.paymentMethod);
        j.put("discount", order.discount);
        j.put("shippingFee", order.shippingFee);
        j.put("createdAt", order.createdAt);
        return j;
    }

    private static MyReview reviewFromJson(JSONObject j) {
        MyReview review = new MyReview(
                j.optLong("id"),
                j.optString("orderNo"),
                j.optString("name"),
                j.optString("option"),
                (float) j.optDouble("rating"),
                j.optString("content"),
                j.optString("date")
        );
        review.accountId = j.optString("accountId");
        review.author = j.optString("author");
        review.productId = j.optInt("productId");
        review.serverId = j.optString("serverId");
        return review;
    }

    private static void saveReviews() {
        if (storage == null) {
            return;
        }
        try {
            JSONArray reviewArray = new JSONArray();
            for (MyReview review : reviews) {
                JSONObject j = new JSONObject();

                j.put("id", review.id);
                j.put("orderNo", review.orderNo);
                j.put("name", review.productName);
                j.put("option", review.option);
                j.put("rating", review.rating);
                j.put("content", review.content);
                j.put("date", review.writtenDate);
                j.put("accountId", review.accountId);
                j.put("author", review.author);
                j.put("productId", review.productId);
                j.put("serverId", review.serverId);

                reviewArray.put(j);
            }
            storage.edit()
                    .putString(KEY_REVIEWS, reviewArray.toString())
                    .putLong(KEY_NEXT_ID, nextId)
                    .apply();
        } catch (Exception e) {
            Log.e("MyPageStore", "주문/리뷰 저장 실패", e);
        }
    }

    private static synchronized void save() {
        if (storage == null) {
            return;
        }

        try {
            JSONObject data = new JSONObject();

            JSONArray orderArray = new JSONArray();
            for (Order order : orders) {
                orderArray.put(orderToJson(order));
            }
            data.put("orders", orderArray);

            JSONArray claimArray = new JSONArray();
            for (Claim claim : claims) {
                JSONObject j = new JSONObject();
                j.put("no", claim.claimNo);
                j.put("type", claim.type);
                j.put("orderNo", claim.orderNo);
                j.put("name", claim.productName);
                j.put("option", claim.option);
                j.put("price", claim.price);
                j.put("date", claim.requestDate);
                j.put("reason", claim.reason);
                j.put("state", claim.state);
                claimArray.put(j);
            }
            data.put("claims", claimArray);

            JSONArray addressArray = new JSONArray();
            for (Address address : addresses) {
                JSONObject j = new JSONObject();
                j.put("id", address.id);
                j.put("label", address.label);
                j.put("receiver", address.receiver);
                j.put("phone", address.phone);
                j.put("zip", address.zipCode);
                j.put("address1", address.address1);
                j.put("address2", address.address2);
                j.put("request", address.request);
                j.put("default", address.isDefault);
                addressArray.put(j);
            }
            data.put("addresses", addressArray);

            JSONArray cardArray = new JSONArray();
            for (PaymentCard card : cards) {
                JSONObject j = new JSONObject();
                j.put("id", card.id);
                j.put("company", card.company);
                j.put("lastFour", card.lastFour);
                j.put("expiry", card.expiry);
                j.put("default", card.isDefault);
                cardArray.put(j);
            }
            data.put("cards", cardArray);

            JSONArray refundArray = new JSONArray();
            for (RefundAccount account : refundAccounts) {
                JSONObject j = new JSONObject();
                j.put("bank", account.bank);
                j.put("accountNo", account.accountNo);
                j.put("holder", account.holder);
                j.put("default", account.isDefault);
                refundArray.put(j);
            }
            data.put("refundAccounts", refundArray);

            JSONArray inquiryArray = new JSONArray();
            for (Inquiry inquiry : inquiries) {
                JSONObject j = new JSONObject();
                j.put("type", inquiry.type);
                j.put("title", inquiry.title);
                j.put("content", inquiry.content);
                j.put("email", inquiry.email);
                j.put("status", inquiry.status);
                j.put("date", inquiry.date);
                j.put("createdAt", inquiry.createdAt);
                j.put("answer", inquiry.answer);
                inquiryArray.put(j);
            }
            data.put("inquiries", inquiryArray);

            data.put("coupons", new JSONArray(receivedCouponIds));
            data.put("usedCoupons", new JSONArray(usedCouponIds));
            data.put("kakaoPay", kakaoPayLinked);
            data.put("naverPay", naverPayLinked);

            storage.edit()
                    .putString(KEY_DATA + accountKey, data.toString())
                    .putLong(KEY_NEXT_ID, nextId)
                    .apply();

        } catch (Exception e) {
            Log.e("MyPageStore", "주문/리뷰 저장 실패", e);
        }
    }

    public static synchronized void deleteAccountData(String key) {
        if (storage == null || key == null) {
            return;
        }
        for (int i = reviews.size() - 1; i >= 0; i--) {
            if (key.equals(reviews.get(i).accountId)) {
                reviews.remove(i);
            }
        }
        saveReviews();
        storage.edit().remove(KEY_DATA + key).apply();
        if (key.equals(accountKey)) {
            clearAccountData();
        }
    }

    // 회원정보
    public static User getUser() {
        return user;
    }

    public static void setLoginUser(
            String name,
            String email,
            String phone
    ) {
        user.name = name == null ? "" : name.trim();
        user.email = email == null ? "" : email.trim();
        user.phone = phone == null ? "" : phone.trim();
        if (appContext != null) {
            user.nickname = LoginSession.getNickname(appContext);
        }
    }

    public static void updateProfile(
            String name,
            String email,
            String phone
    ) {
        user.name = name;
        user.email = email;
        user.phone = phone;
    }

    public static boolean checkPassword(String password) {
        // DB 연동 전 임시 처리
        if (appContext == null) {
            return false;
        }
        return AccountStore.checkPassword(appContext, LoginSession.getUserId(appContext), password);
    }

    public static boolean changePassword(String newPassword) {
        if (appContext == null) {
            return false;
        }
        boolean changed = AccountStore.changePassword(appContext, LoginSession.getUserId(appContext), newPassword);
        if (changed) {
            user.password = "";
        }
        return changed;
    }

    public static void logout() {
        user = new User("", "", "", "");
        if (storage != null) {
            loadAccount("guest");
        } else {
            clearAccountData();
        }
    }

    // 문의
    public static List<Inquiry> getInquiries() {
        refreshInquiries();
        return new ArrayList<>(inquiries);
    }

    public static void addInquiry(
            String type,
            String title,
            String content,
            String email
    ) {
        inquiries.add(
                0,
                new Inquiry(
                        type,
                        title,
                        content,
                        email,
                        INQUIRY_WAITING,
                        today()
                )
        );
        save();
    }

    private static void refreshInquiries() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Inquiry inquiry : inquiries) {
            if (INQUIRY_WAITING.equals(inquiry.status)
                    && now - inquiry.createdAt >= INQUIRY_ANSWER_MILLIS) {
                inquiry.status = INQUIRY_ANSWERED;
                inquiry.answer = autoAnswer(inquiry);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static String autoAnswer(Inquiry inquiry) {
        String body;
        switch (inquiry.type) {
            case "배송":
                body = "주문하신 상품은 결제 후 영업일 기준 2~3일 안에 받아보실 수 있어요. 배송 상태는 마이페이지 > 주문내역에서 확인할 수 있습니다.";
                break;
            case "주문/결제":
                body = "주문·결제 내역은 마이페이지 > 주문내역에서 확인할 수 있어요. 결제완료 상태까지는 상품별로 바로 취소할 수 있습니다.";
                break;
            case "취소/반품/교환":
                body = "배송완료 후 7일 안에 주문내역에서 상품별로 반품·교환을 신청할 수 있어요. 환불은 반품 확인 후 3~5일 안에 진행됩니다.";
                break;
            default:
                body = "남겨 주신 내용을 확인했어요. 더 필요한 내용이 있으면 언제든 다시 문의해 주세요.";
                break;
        }
        return "안녕하세요, 고객센터입니다.\n" + body + "\n감사합니다.";
    }

    // 쿠폰
    public static boolean isCouponReceived(String couponId) {
        return receivedCouponIds.contains(couponId);
    }

    public static boolean receiveCoupon(String couponId) {
        boolean added = receivedCouponIds.add(couponId);
        if (added) {
            save();
        }
        return added;
    }

    public static List<String> getReceivedCouponIds() {
        return new ArrayList<>(receivedCouponIds);
    }

    public static boolean isCouponUsed(String couponId) {
        return usedCouponIds.contains(couponId);
    }

    public static void useCoupon(String couponId) {
        if (couponId != null && usedCouponIds.add(couponId)) {
            save();
        }
    }

    // 주문
    public static synchronized void addOrder(Order order) {
        if (order == null) {
            return;
        }

        if (findOrder(order.orderNo) != null) {
            return;
        }

        orders.add(0, order);
        save();
    }

    public static synchronized void addOrders(List<Order> newOrders) {
        for (int i = newOrders.size() - 1; i >= 0; i--) {
            Order order = newOrders.get(i);
            if (findOrder(order.orderNo) == null) {
                orders.add(0, order);
            }
        }
        save();
    }

    public static synchronized void refreshStatuses() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Order order : orders) {
            if (order.createdAt <= 0
                    || order.status < STATUS_PAID || order.status >= STATUS_DONE) {
                continue;
            }
            int steps = (int) Math.min(3, (now - order.createdAt) / STATUS_STEP_MILLIS);
            int target = STATUS_PAID + steps;
            if (target > order.status) {
                order.status = target;
                changed = true;
            }
            if (order.status >= STATUS_SHIPPING
                    && (order.trackingNo == null || order.trackingNo.isEmpty())) {
                order.courier = COURIER;
                order.trackingNo = trackingNumber(order);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static String trackingNumber(Order order) {
        long seed = Math.abs((order.orderNo + order.createdAt).hashCode());
        return String.format(Locale.ROOT, "6%011d", seed % 100000000000L);
    }

    public static List<Order> getOrders(int status) {
        refreshStatuses();
        List<Order> result = new ArrayList<>();

        for (Order order : orders) {
            if (status == STATUS_ALL || order.status == status) {
                result.add(order);
            }
        }

        return result;
    }

    public static List<Order> getOrderGroup(String groupNo) {
        List<Order> result = new ArrayList<>();
        for (Order order : orders) {
            if (order.groupNo.equals(groupNo)) {
                result.add(order);
            }
        }
        return result;
    }

    public static int countOrders(int status) {
        return getOrders(status).size();
    }

    public static Order findOrder(String orderNo) {
        if (orderNo == null) {
            return null;
        }

        for (Order order : orders) {
            if (order.orderNo.equals(orderNo)) {
                return order;
            }
        }

        return null;
    }

    public static boolean canCancel(Order order) {
        return order != null
                && (order.status == STATUS_PENDING
                || order.status == STATUS_PAID);
    }

    public static boolean canReturnOrExchange(Order order) {
        return order != null && order.status == STATUS_DONE;
    }

    // 취소 / 반품 / 교환
    public static void requestClaim(
            Order order,
            String type,
            String reason
    ) {
        if (order == null) {
            return;
        }

        String state;

        if (CLAIM_CANCEL.equals(type)) {
            order.status = STATUS_CANCELED;
            state = CLAIM_STATE_DONE;

        } else if (CLAIM_RETURN.equals(type)) {
            order.status = STATUS_RETURNING;
            state = CLAIM_STATE_PROCESSING;

        } else {
            order.status = STATUS_EXCHANGING;
            state = CLAIM_STATE_PROCESSING;
        }

        String claimNo = "C" + order.orderNo
                + "-" + (claims.size() + 1);

        claims.add(
                0,
                new Claim(
                        claimNo,
                        type,
                        order.orderNo,
                        order.productName,
                        order.option,
                        order.getPaidPrice(),
                        today(),
                        reason,
                        state
                )
        );

        save();
    }

    public static List<Claim> getClaims(String type) {
        List<Claim> result = new ArrayList<>();

        for (Claim claim : claims) {
            if (type == null || claim.type.equals(type)) {
                result.add(claim);
            }
        }

        return result;
    }

    public static void withdrawClaim(Claim claim) {
        if (claim == null) {
            return;
        }

        claim.state = CLAIM_STATE_WITHDRAWN;

        Order order = findOrder(claim.orderNo);

        if (order != null) {
            order.status = STATUS_DONE;
        }
        save();
    }

    // 배송지
    public static List<Address> getAddresses() {
        List<Address> result = new ArrayList<>();

        for (Address address : addresses) {
            if (address.isDefault) {
                result.add(address);
            }
        }

        for (Address address : addresses) {
            if (!address.isDefault) {
                result.add(address);
            }
        }

        return result;
    }

    public static Address getDefaultAddress() {
        for (Address address : addresses) {
            if (address.isDefault) {
                return address;
            }
        }
        return addresses.isEmpty() ? null : addresses.get(0);
    }

    public static Address findAddress(long id) {
        for (Address address : addresses) {
            if (address.id == id) {
                return address;
            }
        }

        return null;
    }

    public static long newId() {
        return nextId++;
    }

    public static void saveAddress(Address address) {
        if (address == null) {
            return;
        }

        if (addresses.isEmpty()) {
            address.isDefault = true;
        }

        if (address.isDefault) {
            clearDefaultAddress();
        }

        Address original = findAddress(address.id);

        if (original == null) {
            addresses.add(address);
        } else {
            addresses.set(addresses.indexOf(original), address);
        }

        ensureDefaultAddress();
        save();
    }

    public static void setDefaultAddress(Address address) {
        clearDefaultAddress();
        address.isDefault = true;
        save();
    }

    public static void deleteAddress(Address address) {
        addresses.remove(address);
        ensureDefaultAddress();
        save();
    }

    private static void clearDefaultAddress() {
        for (Address address : addresses) {
            address.isDefault = false;
        }
    }

    private static void ensureDefaultAddress() {
        for (Address address : addresses) {
            if (address.isDefault) {
                return;
            }
        }

        if (!addresses.isEmpty()) {
            addresses.get(0).isDefault = true;
        }
    }

    // 결제 수단
    public static List<PaymentCard> getCards() {
        return new ArrayList<>(cards);
    }

    public static PaymentCard getDefaultCard() {
        for (PaymentCard card : cards) {
            if (card.isDefault) {
                return card;
            }
        }
        return cards.isEmpty() ? null : cards.get(0);
    }

    public static void addCard(
            String company,
            String cardNumber,
            String expiry
    ) {
        String lastFour = cardNumber.substring(
                cardNumber.length() - 4
        );

        cards.add(
                new PaymentCard(
                        nextId++,
                        company,
                        lastFour,
                        expiry,
                        cards.isEmpty()
                )
        );
        save();
    }

    public static void setDefaultCard(PaymentCard card) {
        for (PaymentCard c : cards) {
            c.isDefault = false;
        }

        card.isDefault = true;
        save();
    }

    public static void deleteCard(PaymentCard card) {
        cards.remove(card);

        if (card.isDefault && !cards.isEmpty()) {
            cards.get(0).isDefault = true;
        }
        save();
    }

    public static boolean isKakaoPayLinked() {
        return kakaoPayLinked;
    }

    public static void setKakaoPayLinked(boolean linked) {
        kakaoPayLinked = linked;
        save();
    }

    public static boolean isNaverPayLinked() {
        return naverPayLinked;
    }

    public static void setNaverPayLinked(boolean linked) {
        naverPayLinked = linked;
        save();
    }

    // 환불 계좌
    public static List<RefundAccount> getRefundAccounts() {
        return new ArrayList<>(refundAccounts);
    }

    public static RefundAccount getRefundAccount() {
        for (RefundAccount a : refundAccounts) {
            if (a.isDefault) {
                return a;
            }
        }
        return null;
    }

    public static void addRefundAccount(RefundAccount account) {
        account.isDefault = refundAccounts.isEmpty();
        refundAccounts.add(account);
        save();
    }

    public static boolean hasRefundAccount(String bank, String accountNo) {
        for (RefundAccount a : refundAccounts) {
            if (a.bank.equals(bank) && a.accountNo.equals(accountNo)) {
                return true;
            }
        }
        return false;
    }

    public static void setDefaultRefundAccount(RefundAccount account) {
        for (RefundAccount a : refundAccounts) {
            a.isDefault = false;
        }

        account.isDefault = true;
        save();
    }

    public static void deleteRefundAccount(RefundAccount account) {
        refundAccounts.remove(account);

        if (account.isDefault && !refundAccounts.isEmpty()) {
            refundAccounts.get(0).isDefault = true;
        }
        save();
    }

    // 리뷰
    public static List<Order> getWritableReviewOrders() {
        refreshStatuses();
        List<Order> result = new ArrayList<>();

        for (Order order : orders) {
            if (order.status == STATUS_DONE && !order.reviewed) {
                result.add(order);
            }
        }

        return result;
    }

    public static List<MyReview> getMyReviews() {
        List<MyReview> result = new ArrayList<>();
        for (MyReview review : reviews) {
            if (accountKey.equals(review.accountId)) {
                result.add(review);
            }
        }
        return result;
    }

    public static List<MyReview> getProductReviews(int productId, String serverId) {
        List<MyReview> result = new ArrayList<>();
        for (MyReview review : reviews) {
            boolean sameServer = serverId != null && !serverId.isEmpty()
                    && serverId.equals(review.serverId);
            boolean sameLocal = (serverId == null || serverId.isEmpty())
                    && review.serverId.isEmpty() && productId > 0 && review.productId == productId;
            if (sameServer || sameLocal) {
                result.add(review);
            }
        }
        return result;
    }

    public static MyReview findReview(long id) {
        for (MyReview review : reviews) {
            if (review.id == id) {
                return review;
            }
        }

        return null;
    }

    public static void addReview(
            Order order,
            float rating,
            String content
    ) {
        if (order == null || order.reviewed) {
            return;
        }

        MyReview review = new MyReview(
                nextId++,
                order.orderNo,
                order.productName,
                order.option,
                rating,
                content,
                today()
        );
        review.accountId = accountKey;
        review.author = user.nickname.isEmpty() ? maskName(user.name) : user.nickname;
        review.productId = order.productId;
        review.serverId = order.serverId;

        reviews.add(0, review);

        order.reviewed = true;
        saveReviews();
        save();
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "구매자";
        }
        return name.charAt(0) + "**";
    }

    public static void updateReview(
            MyReview review,
            float rating,
            String content
    ) {
        if (review == null) {
            return;
        }

        review.rating = rating;
        review.content = content;
        review.writtenDate = today();

        saveReviews();
    }

    public static void deleteReview(MyReview review) {
        if (review == null) {
            return;
        }

        reviews.remove(review);

        Order order = findOrder(review.orderNo);

        if (order != null) {
            order.reviewed = false;
        }

        saveReviews();
        save();
    }

    // 공지사항 / FAQ
    public static List<Notice> getNotices() {
        return new ArrayList<>(notices);
    }

    public static List<Notice> getFaqs() {
        return new ArrayList<>(faqs);
    }

    private static Notice faq(
            String category,
            String question,
            String answer
    ) {
        return new Notice(category, question, "", answer);
    }

    // 공통
    public static String formatPrice(int price) {
        return new DecimalFormat("#,###").format(price) + "원";
    }

    public static String today() {
        return new SimpleDateFormat(
                "yyyy.MM.dd",
                Locale.KOREA
        ).format(new Date());
    }

    private MyPageStore() {
    }
}
