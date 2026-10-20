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
        public final String email;
        public String status;
        public final String date;

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

    private static RefundAccount refundAccount = null;
    private static boolean kakaoPayLinked = false;
    private static boolean naverPayLinked = false;

    private static long nextId = 1;

    // FAQ 기본 데이터
    private static final List<Notice> faqs = new ArrayList<>(
            Arrays.asList(
                    faq(
                            "가상피팅",
                            "가상피팅은 어떻게 이용하나요?",
                            "하단 「피팅」 메뉴에서 키와 전신 사진을 입력하면 내 체형에 맞는 3D 아바타가 만들어집니다. 아바타가 만들어지면 원하는 옷을 입혀 핏을 확인할 수 있습니다."
                    ),
                    faq(
                            "가상피팅",
                            "내 아바타 치수가 실제와 달라요.",
                            "아바타에서 고치고 싶은 부위를 터치하면 확대되고, 슬라이더나 직접 입력으로 cm 단위까지 조정할 수 있습니다."
                    ),
                    faq(
                            "가상피팅",
                            "전신 사진은 어떻게 찍어야 정확한가요?",
                            "몸에 붙는 옷을 입고, 밝고 단순한 배경 앞에서 정면 전신이 모두 나오도록 찍어주세요. 카메라는 허리 높이에서 수평으로 들면 가장 정확합니다."
                    ),
                    faq(
                            "가상피팅",
                            "촬영한 사진은 저장되나요?",
                            "촬영한 사진은 신체 치수 추정에만 사용되며, 보관 여부와 기간은 개인정보처리방침을 따릅니다."
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

    // 주문 / 리뷰 영구 저장
    private static SharedPreferences storage;

    public static synchronized void init(Context context) {
        if (storage != null) {
            return;
        }

        storage = context.getApplicationContext()
                .getSharedPreferences(
                        "capstone_order_review_store",
                        Context.MODE_PRIVATE
                );

        orders.clear();
        reviews.clear();

        try {
            JSONArray savedOrders = new JSONArray(
                    storage.getString("orders", "[]")
            );

            for (int i = 0; i < savedOrders.length(); i++) {
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

            JSONArray savedReviews = new JSONArray(
                    storage.getString("reviews", "[]")
            );

            for (int i = 0; i < savedReviews.length(); i++) {
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

        } catch (Exception e) {
            Log.e("MyPageStore", "주문/리뷰 불러오기 실패", e);
        }
    }

    private static synchronized void saveOrderReviews() {
        if (storage == null) {
            return;
        }

        JSONArray orderArray = new JSONArray();
        JSONArray reviewArray = new JSONArray();

        try {
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

            storage.edit()
                    .putString("orders", orderArray.toString())
                    .putString("reviews", reviewArray.toString())
                    .apply();

        } catch (Exception e) {
            Log.e("MyPageStore", "주문/리뷰 저장 실패", e);
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
        return true;
    }

    public static void changePassword(String newPassword) {
        user.password = newPassword;
    }

    public static void logout() {
        user = new User("", "", "", "");
        receivedCouponIds.clear();
        inquiries.clear();
    }

    // 문의
    public static List<Inquiry> getInquiries() {
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
    }

    // 쿠폰
    public static boolean isCouponReceived(String couponId) {
        return receivedCouponIds.contains(couponId);
    }

    public static boolean receiveCoupon(String couponId) {
        return receivedCouponIds.add(couponId);
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
        saveOrderReviews();
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

        saveOrderReviews();
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
            saveOrderReviews();
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
    }

    public static void setDefaultAddress(Address address) {
        clearDefaultAddress();
        address.isDefault = true;
    }

    public static void deleteAddress(Address address) {
        addresses.remove(address);
        ensureDefaultAddress();
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
    }

    public static void setDefaultCard(PaymentCard card) {
        for (PaymentCard c : cards) {
            c.isDefault = false;
        }

        card.isDefault = true;
    }

    public static void deleteCard(PaymentCard card) {
        cards.remove(card);

        if (card.isDefault && !cards.isEmpty()) {
            cards.get(0).isDefault = true;
        }
    }

    public static boolean isKakaoPayLinked() {
        return kakaoPayLinked;
    }

    public static void setKakaoPayLinked(boolean linked) {
        kakaoPayLinked = linked;
    }

    public static boolean isNaverPayLinked() {
        return naverPayLinked;
    }

    public static void setNaverPayLinked(boolean linked) {
        naverPayLinked = linked;
    }

    // 환불 계좌
    public static RefundAccount getRefundAccount() {
        return refundAccount;
    }

    public static void saveRefundAccount(RefundAccount account) {
        refundAccount = account;
    }

    public static void deleteRefundAccount() {
        refundAccount = null;
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
        saveOrderReviews();
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

        saveOrderReviews();
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

        saveOrderReviews();
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
