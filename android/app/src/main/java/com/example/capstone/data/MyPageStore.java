package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
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

        public final String courier;
        public final String trackingNo;

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
        public String status;
        public final String date;
        public String answer = "";

        public Inquiry(
                String type,
                String title,
                String content,
                String status,
                String date
        ) {
            this.type = type;
            this.title = title;
            this.content = content;
            this.status = status;
            this.date = date;
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

    private static final Set<String> receivedCouponIds = new HashSet<>();

    private static final List<RefundAccount> refundAccounts = new ArrayList<>();
    private static boolean kakaoPayLinked = false;
    private static boolean naverPayLinked = false;

    private static long nextId = 1;

    // FAQ 기본 데이터
    private static final List<Notice> faqs = new ArrayList<>(
            Arrays.asList(
                    faq(
                            "체형분석",
                            "체형분석은 어떻게 이용하나요?",
                            "로그인한 뒤 하단 「체형」 메뉴에서 「새 체형 추가하기」를 누르세요. 키·몸무게·나이를 입력하고 7개 설문에 답하면 나의 골격 체형(스트레이트·웨이브·내추럴)을 알려 드려요. 결과는 홈의 「체형 맞춤」 추천에 바로 쓰여요."
                    ),
                    faq(
                            "체형분석",
                            "골격 체형은 어떻게 정해지나요?",
                            "골격 체형은 설문 답변으로 정해져요. 한 체형이 확실히 높으면 그 체형으로, 두 체형이 비슷하면 혼합형으로 보여 드려요. 사진으로 잰 치수는 골격 체형을 바꾸지 않고, 사이즈를 확인하는 데만 쓰여요."
                    ),
                    faq(
                            "체형분석",
                            "「사진으로 정밀 분석하기」와 「이대로 저장하기」는 뭐가 다른가요?",
                            "「이대로 저장하기」는 설문 결과만 내 체형으로 저장해요. 「사진으로 정밀 분석하기」는 정면·옆면 전신 사진으로 가슴·허리·골반 등의 치수까지 재서 저장해요. 치수가 있으면 상품 상세의 추천 사이즈와 주문 전 사이즈 확인을 받을 수 있어요. 저장하지 않고 나가면 결과는 남지 않아요."
                    ),
                    faq(
                            "체형분석",
                            "체형을 여러 개 저장할 수 있나요?",
                            "최대 10개까지 저장할 수 있어요. 「내 체형관리」에서 체형을 눌러 「현재 체형」으로 고르면, 홈 체형 맞춤 추천과 장바구니·주문 사이즈 확인이 그 체형 기준으로 바뀌어요. 이름 변경과 삭제도 같은 화면에서 할 수 있어요."
                    ),
                    faq(
                            "체형분석",
                            "전신 사진은 어떻게 고르면 정확한가요?",
                            "머리부터 발끝까지 모두 나오고 몸에 붙는 옷을 입은 정면·옆면 사진을 골라 주세요. 카메라를 기울이지 않고 찍은 사진일수록, 밝고 단순한 배경일수록 정확해요. 정면과 옆면은 같은 사람의 사진이어야 해요."
                    ),
                    faq(
                            "체형분석",
                            "사진으로 잰 치수가 실제와 달라요.",
                            "옷의 두께, 자세, 사진 각도에 따라 오차가 생길 수 있어요. 치수를 잰 뒤 나오는 화면에서 가슴·허리·골반 선을 위아래로 끌어 몸에 맞게 조정하면 치수가 다시 계산돼요. 그래도 다르면 몸에 붙는 옷을 입고 찍은 사진으로 다시 분석해 주세요. 결과는 참고용이니 상품의 실측 사이즈도 함께 확인해 주세요."
                    ),
                    faq(
                            "체형분석",
                            "선택한 사진은 저장되나요?",
                            "사진은 치수를 계산하는 데만 쓰이고 서버로 보내지 않아요. 계산에 쓴 사진 정보는 측정이 끝나면 앱의 메모리에서 지워져요. 저장되는 것은 설문 답변과 계산된 치수뿐이에요."
                    ),
                    faq(
                            "체형분석",
                            "추천 사이즈나 사이즈 경고를 끄고 싶어요.",
                            "「내 체형관리」 아래쪽의 체형 설정에서 「추천 사이즈 표시」, 「사이즈 경고 팝업」, 「측정 권유 팝업」을 각각 켜고 끌 수 있어요. 「새 체형을 현재 체형으로 자동 선택」도 여기에서 바꿀 수 있어요."
                    ),
                    faq(
                            "체형분석",
                            "체형분석 결과와 실제 착용감이 다를 수 있나요?",
                            "옷의 소재, 신축성, 개인 체형 차이에 따라 실제 착용감은 다를 수 있어요. 체형 맞춤 추천과 추천 사이즈는 참고용이니 상품 상세의 실측 사이즈도 함께 확인해 주세요."
                    ),
                    faq(
                            "주문/결제",
                            "어떤 결제 수단을 쓸 수 있나요?",
                            "신용/체크카드로 결제할 수 있습니다. 결제 수단은 마이페이지 > 결제 수단에서 관리할 수 있습니다."
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
                            "결제완료 상태까지는 주문내역에서 바로 취소할 수 있습니다. 배송준비중부터는 1:1 문의로 요청해주세요."
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
                            "아이디/비밀번호를 잊어버렸어요.",
                            "로그인 화면의 아이디 찾기, 비밀번호 재설정에서 찾을 수 있습니다."
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

    // 계정별 영구 저장
    // 한 계정의 기록(주문·리뷰·반품·배송지·카드·환불 계좌·문의·받은 쿠폰)을
    // 같은 SharedPreferences의 "data@계정" 한 곳에 JSON으로 저장한다.
    // 계정 이름은 다른 계정별 기록과 같은 BodyProfileStore.accountKey (비로그인은 guest)
    private static final String STORAGE_NAME = "capstone_order_review_store";
    private static final String DATA_KEY = "data@";
    private static final String LEGACY_ORDERS = "orders";     // 예전: 계정 구분 없이 기기에 하나
    private static final String LEGACY_REVIEWS = "reviews";

    private static SharedPreferences storage;
    private static Context appContext;
    private static String loadedAccount;

    /**
     * 지금 로그인한 계정의 기록을 불러온다. 이미 그 계정이면 아무것도 안 한다.
     * 계정이 바뀌었으면 이전 계정의 메모리 기록을 비우고 새 계정 것을 읽는다.
     * (앱 시작, 로그인, 로그아웃, 주문 화면에서 부른다)
     */
    public static synchronized void init(Context context) {
        appContext = context.getApplicationContext();
        storage = appContext.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);

        String account = BodyProfileStore.accountKey(appContext);
        if (account.equals(loadedAccount)) {
            return;
        }

        clearMemory();
        loadedAccount = account;
        migrateLegacy(account);

        try {
            JSONObject all = new JSONObject(storage.getString(DATA_KEY + account, "{}"));
            readAll(all);
        } catch (Exception e) {
            Log.e("MyPageStore", "내 기록 불러오기 실패", e);
        }
    }

    /** 메모리에 있는 모든 계정 기록을 비운다 (저장된 기록은 그대로) */
    private static void clearMemory() {
        user = new User("", "", "", "");
        orders.clear();
        claims.clear();
        addresses.clear();
        cards.clear();
        reviews.clear();
        inquiries.clear();
        receivedCouponIds.clear();
        refundAccounts.clear();
        kakaoPayLinked = false;
        naverPayLinked = false;
        nextId = 1;
    }

    /** 예전에 계정 구분 없이 저장한 주문·리뷰는 처음 불러오는 로그인 계정으로 한 번 옮긴다 */
    private static void migrateLegacy(String account) {
        if ("guest".equals(account)
                || storage.contains(DATA_KEY + account)
                || (!storage.contains(LEGACY_ORDERS) && !storage.contains(LEGACY_REVIEWS))) {
            return;
        }

        try {
            JSONObject all = new JSONObject();
            all.put("orders", new JSONArray(storage.getString(LEGACY_ORDERS, "[]")));
            all.put("reviews", new JSONArray(storage.getString(LEGACY_REVIEWS, "[]")));
            storage.edit()
                    .putString(DATA_KEY + account, all.toString())
                    .remove(LEGACY_ORDERS)
                    .remove(LEGACY_REVIEWS)
                    .apply();
        } catch (Exception e) {
            Log.e("MyPageStore", "예전 주문/리뷰 옮기기 실패", e);
        }
    }

    private static void readAll(JSONObject all) throws Exception {
        JSONArray savedOrders = all.optJSONArray("orders");
        for (int i = 0; savedOrders != null && i < savedOrders.length(); i++) {
            JSONObject j = savedOrders.getJSONObject(i);

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
            orders.add(order);
        }

        JSONArray savedReviews = all.optJSONArray("reviews");
        for (int i = 0; savedReviews != null && i < savedReviews.length(); i++) {
            JSONObject j = savedReviews.getJSONObject(i);

            long id = j.optLong("id");

            reviews.add(
                    new MyReview(
                            id,
                            j.optString("orderNo"),
                            j.optString("name"),
                            j.optString("option"),
                            (float) j.optDouble("rating"),
                            j.optString("content"),
                            j.optString("date")
                    )
            );

            nextId = Math.max(nextId, id + 1);
        }

        JSONArray savedClaims = all.optJSONArray("claims");
        for (int i = 0; savedClaims != null && i < savedClaims.length(); i++) {
            JSONObject j = savedClaims.getJSONObject(i);

            claims.add(
                    new Claim(
                            j.optString("no"),
                            j.optString("type"),
                            j.optString("orderNo"),
                            j.optString("name"),
                            j.optString("option"),
                            j.optInt("price"),
                            j.optString("date"),
                            j.optString("reason"),
                            j.optString("state")
                    )
            );
        }

        JSONArray savedAddresses = all.optJSONArray("addresses");
        for (int i = 0; savedAddresses != null && i < savedAddresses.length(); i++) {
            JSONObject j = savedAddresses.getJSONObject(i);

            long id = j.optLong("id");

            addresses.add(
                    new Address(
                            id,
                            j.optString("label"),
                            j.optString("receiver"),
                            j.optString("phone"),
                            j.optString("zipCode"),
                            j.optString("address1"),
                            j.optString("address2"),
                            j.optString("request"),
                            j.optBoolean("isDefault")
                    )
            );

            nextId = Math.max(nextId, id + 1);
        }

        JSONArray savedCards = all.optJSONArray("cards");
        for (int i = 0; savedCards != null && i < savedCards.length(); i++) {
            JSONObject j = savedCards.getJSONObject(i);

            long id = j.optLong("id");

            cards.add(
                    new PaymentCard(
                            id,
                            j.optString("company"),
                            j.optString("lastFour"),
                            j.optString("expiry"),
                            j.optBoolean("isDefault")
                    )
            );

            nextId = Math.max(nextId, id + 1);
        }

        JSONArray savedRefunds = all.optJSONArray("refundAccounts");
        for (int i = 0; savedRefunds != null && i < savedRefunds.length(); i++) {
            JSONObject j = savedRefunds.getJSONObject(i);

            RefundAccount account = new RefundAccount(
                    j.optString("bank"),
                    j.optString("accountNo"),
                    j.optString("holder")
            );
            account.isDefault = j.optBoolean("isDefault");
            refundAccounts.add(account);
        }

        JSONArray savedInquiries = all.optJSONArray("inquiries");
        for (int i = 0; savedInquiries != null && i < savedInquiries.length(); i++) {
            JSONObject j = savedInquiries.getJSONObject(i);

            Inquiry inquiry = new Inquiry(
                    j.optString("type"),
                    j.optString("title"),
                    j.optString("content"),
                    j.optString("status"),
                    j.optString("date")
            );
            inquiry.answer = j.optString("answer");
            inquiries.add(inquiry);
        }

        JSONArray savedCoupons = all.optJSONArray("coupons");
        for (int i = 0; savedCoupons != null && i < savedCoupons.length(); i++) {
            receivedCouponIds.add(savedCoupons.getString(i));
        }

        kakaoPayLinked = all.optBoolean("kakaoPay");
        naverPayLinked = all.optBoolean("naverPay");
    }

    /** 지금 계정의 기록을 저장한다. 기록을 바꾸는 함수는 끝에서 이걸 부른다 */
    private static synchronized void save() {
        if (storage == null || loadedAccount == null) {
            return;
        }

        try {
            JSONObject all = new JSONObject();

            JSONArray orderArray = new JSONArray();
            for (Order order : orders) {
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

                orderArray.put(j);
            }
            all.put("orders", orderArray);

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

                reviewArray.put(j);
            }
            all.put("reviews", reviewArray);

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
            all.put("claims", claimArray);

            JSONArray addressArray = new JSONArray();
            for (Address address : addresses) {
                JSONObject j = new JSONObject();

                j.put("id", address.id);
                j.put("label", address.label);
                j.put("receiver", address.receiver);
                j.put("phone", address.phone);
                j.put("zipCode", address.zipCode);
                j.put("address1", address.address1);
                j.put("address2", address.address2);
                j.put("request", address.request);
                j.put("isDefault", address.isDefault);

                addressArray.put(j);
            }
            all.put("addresses", addressArray);

            JSONArray cardArray = new JSONArray();
            for (PaymentCard card : cards) {
                JSONObject j = new JSONObject();

                j.put("id", card.id);
                j.put("company", card.company);
                j.put("lastFour", card.lastFour);
                j.put("expiry", card.expiry);
                j.put("isDefault", card.isDefault);

                cardArray.put(j);
            }
            all.put("cards", cardArray);

            JSONArray refundArray = new JSONArray();
            for (RefundAccount account : refundAccounts) {
                JSONObject j = new JSONObject();

                j.put("bank", account.bank);
                j.put("accountNo", account.accountNo);
                j.put("holder", account.holder);
                j.put("isDefault", account.isDefault);

                refundArray.put(j);
            }
            all.put("refundAccounts", refundArray);

            JSONArray inquiryArray = new JSONArray();
            for (Inquiry inquiry : inquiries) {
                JSONObject j = new JSONObject();

                j.put("type", inquiry.type);
                j.put("title", inquiry.title);
                j.put("content", inquiry.content);
                j.put("status", inquiry.status);
                j.put("date", inquiry.date);
                j.put("answer", inquiry.answer);

                inquiryArray.put(j);
            }
            all.put("inquiries", inquiryArray);

            all.put("coupons", new JSONArray(receivedCouponIds));
            all.put("kakaoPay", kakaoPayLinked);
            all.put("naverPay", naverPayLinked);

            storage.edit()
                    .putString(DATA_KEY + loadedAccount, all.toString())
                    .apply();

        } catch (Exception e) {
            Log.e("MyPageStore", "내 기록 저장 실패", e);
        }
    }

    /** 이메일·전화번호가 바뀌어 계정 이름이 바뀌었다: 기록을 새 이름으로 옮긴다 */
    public static synchronized void moveAccount(Context context, String from, String to) {
        if (from.equals(to)) {
            return;
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(DATA_KEY + from, null);

        if (saved != null) {
            prefs.edit()
                    .putString(DATA_KEY + to, saved)
                    .remove(DATA_KEY + from)
                    .apply();
        }

        if (from.equals(loadedAccount)) {
            loadedAccount = to;   // 메모리의 기록은 그대로 새 계정 것이 된다
        }
    }

    /** 탈퇴한 회원의 주문·반품 기록을 개인정보 없이 보관하는 곳 (어느 계정에도 연결하지 않고, 화면에서 읽지 않는다) */
    private static final String ARCHIVE_KEY = "archived_orders";

    /**
     * 탈퇴: 이 계정의 기록을 지운다 (메모리에 불러온 것도 비운다).
     * 다만 주문·반품 기록은 법령에 따라 보관해야 하므로, 이름·번호·주소 같은 개인정보와 계정 연결을 뺀 채
     * 따로 남긴다 (ARCHIVE_KEY). 배송지·카드·환불 계좌·문의·리뷰·쿠폰은 모두 지운다.
     */
    public static synchronized void deleteAccount(Context context, String account) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);

        archiveOrders(prefs, prefs.getString(DATA_KEY + account, null));

        prefs.edit().remove(DATA_KEY + account).apply();

        if (account.equals(loadedAccount)) {
            clearMemory();
            loadedAccount = null;
        }
    }

    /** 저장된 기록(JSON)에서 주문·반품만 골라, 개인정보를 뺀 보관용 기록으로 덧붙인다 */
    private static void archiveOrders(SharedPreferences prefs, String savedData) {

        if (savedData == null) {
            return;
        }

        try {
            JSONObject all = new JSONObject(savedData);
            JSONArray orders = all.optJSONArray("orders");
            JSONArray claims = all.optJSONArray("claims");

            if (orders == null || orders.length() == 0) {
                return;
            }

            JSONArray archive = new JSONArray(prefs.getString(ARCHIVE_KEY, "[]"));
            long now = System.currentTimeMillis();

            for (int i = 0; i < orders.length(); i++) {
                JSONObject o = orders.getJSONObject(i);

                // 거래 내용만 남긴다 (회원 이름·연락처·주소는 처음부터 주문 기록에 없다)
                JSONObject record = new JSONObject();
                record.put("no", o.optString("no"));
                record.put("date", o.optString("date"));
                record.put("brand", o.optString("brand"));
                record.put("name", o.optString("name"));
                record.put("option", o.optString("option"));
                record.put("quantity", o.optInt("quantity", 1));
                record.put("price", o.optInt("price"));
                record.put("status", o.optInt("status"));
                record.put("archivedAt", now);

                // 같은 주문의 반품·교환·취소 기록 (사유 글에는 개인정보가 섞일 수 있어 뺀다)
                JSONArray orderClaims = new JSONArray();
                for (int j = 0; claims != null && j < claims.length(); j++) {
                    JSONObject c = claims.getJSONObject(j);
                    if (o.optString("no").equals(c.optString("orderNo"))) {
                        JSONObject claim = new JSONObject();
                        claim.put("no", c.optString("no"));
                        claim.put("type", c.optString("type"));
                        claim.put("date", c.optString("date"));
                        claim.put("state", c.optString("state"));
                        orderClaims.put(claim);
                    }
                }
                record.put("claims", orderClaims);

                archive.put(record);
            }

            prefs.edit().putString(ARCHIVE_KEY, archive.toString()).apply();

        } catch (Exception e) {
            Log.e("MyPageStore", "주문 기록 보관 실패", e);
        }
    }

    /** 예전 형식 이름의 기록을, 새 이름에 기록이 없을 때만 옮긴다 */
    public static synchronized void adoptAccount(Context context, String from, String to) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);

        if (from.equals(to) || prefs.contains(DATA_KEY + to)) {
            return;
        }

        moveAccount(context, from, to);
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

    /** 로그아웃·탈퇴: 메모리의 기록을 모두 비우고, 지금 계정(비로그인)의 기록을 다시 불러온다 */
    public static synchronized void logout() {
        clearMemory();
        loadedAccount = null;

        if (appContext != null) {
            init(appContext);
        }
    }

    // 문의
    public static List<Inquiry> getInquiries() {
        return new ArrayList<>(inquiries);
    }

    public static void addInquiry(
            String type,
            String title,
            String content
    ) {
        inquiries.add(
                0,
                new Inquiry(
                        type,
                        title,
                        content,
                        INQUIRY_WAITING,
                        today()
                )
        );

        save();
    }

    public static void addInquiry(Inquiry inquiry) {
        if (inquiry == null) {
            return;
        }
        inquiries.add(0, inquiry);
        save();
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

    public static List<Order> getOrders(int status) {
        List<Order> result = new ArrayList<>();

        for (Order order : orders) {
            if (status == STATUS_ALL || order.status == status) {
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
                        order.getTotalPrice(),
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
            save();
        }
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
        List<Order> result = new ArrayList<>();

        for (Order order : orders) {
            if (order.status == STATUS_DONE && !order.reviewed) {
                result.add(order);
            }
        }

        return result;
    }

    public static List<MyReview> getMyReviews() {
        return new ArrayList<>(reviews);
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

        reviews.add(
                0,
                new MyReview(
                        nextId++,
                        order.orderNo,
                        order.productName,
                        order.option,
                        rating,
                        content,
                        today()
                )
        );

        order.reviewed = true;
        save();
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

        save();
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
