package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.Coupons;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.InputRules;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.Product;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.ShopStore;
import com.example.capstone.data.SizeFitChecker;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.mypage.DeliveryAddressActivity;
import com.example.capstone.mypage.PaymentMethodActivity;

import java.util.ArrayList;
import java.util.List;

public class OrderActivity extends AppCompatActivity {

    private static final String EXTRA_CART_IDS = "cart_item_ids";
    private static final String EXTRA_BUY_PRODUCT = "buy_product_id";
    private static final String EXTRA_BUY_SERVER = "buy_server_id";
    private static final String EXTRA_BUY_SIZE = "buy_size";
    private static final String EXTRA_BUY_COLOR = "buy_color";
    private static final String EXTRA_BUY_QUANTITY = "buy_quantity";

    private static final String PAY_TRANSFER = "계좌이체";
    private static final String PAY_OTHER_CARD = "신용/체크카드 (다른 카드)";

    private TextView btnBack;
    private TextView tvOrderItemsTitle;
    private LinearLayout containerOrderItems;
    private TextView tvAddressLabel;
    private TextView tvOrderPrice;
    private TextView tvDiscountPrice;
    private TextView tvDeliveryPrice;
    private TextView tvTotalPrice;
    private TextView tvCoupon;

    private EditText etReceiver;
    private EditText etPhone;
    private EditText etZipCode;
    private EditText etAddress;
    private EditText etAddressDetail;
    private EditText etRequest;
    private CheckBox cbSaveAddress;

    private RadioGroup rgPayment;
    private Button btnOrder;

    private final List<ShopStore.CartItem> lines = new ArrayList<>();
    private final List<String> paymentLabels = new ArrayList<>();
    private long[] cartIds;

    private Coupons.Coupon selectedCoupon;
    private int productTotal;
    private int discount;
    private int shippingFee;

    private boolean orderCompleted = false;
    private boolean loginAsked = false;
    private boolean addressFilled = false;

    public static Intent cartIntent(Context context, long[] cartIds) {
        Intent intent = new Intent(context, OrderActivity.class);
        intent.putExtra(EXTRA_CART_IDS, cartIds);
        return intent;
    }

    public static Intent buyNowIntent(Context context, Product product, String size, String color, int quantity) {
        Intent intent = new Intent(context, OrderActivity.class);
        intent.putExtra(EXTRA_BUY_PRODUCT, product.getId());
        intent.putExtra(EXTRA_BUY_SERVER, product.getServerId());
        intent.putExtra(EXTRA_BUY_SIZE, size);
        intent.putExtra(EXTRA_BUY_COLOR, color);
        intent.putExtra(EXTRA_BUY_QUANTITY, quantity);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_order);

        // 주문 및 리뷰 저장소 초기화
        MyPageStore.init(this);

        // XML 연결
        btnBack = findViewById(R.id.btnBack);

        tvOrderItemsTitle = findViewById(R.id.tvOrderItemsTitle);
        containerOrderItems = findViewById(R.id.containerOrderItems);
        tvAddressLabel = findViewById(R.id.tvAddressLabel);

        tvOrderPrice = findViewById(R.id.tvOrderPrice);
        tvDiscountPrice = findViewById(R.id.tvDiscountPrice);
        tvDeliveryPrice = findViewById(R.id.tvDeliveryPrice);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        tvCoupon = findViewById(R.id.tvCoupon);

        etReceiver = findViewById(R.id.etReceiver);
        etPhone = findViewById(R.id.etPhone);
        etZipCode = findViewById(R.id.etZipCode);
        etAddress = findViewById(R.id.etAddress);
        etAddressDetail = findViewById(R.id.etAddressDetail);
        etRequest = findViewById(R.id.etRequest);
        cbSaveAddress = findViewById(R.id.cbSaveAddress);

        rgPayment = findViewById(R.id.rgPayment);
        btnOrder = findViewById(R.id.btnOrder);

        // 장바구니에서 전달한 주문 정보
        loadLines();

        if (lines.isEmpty()) {
            Toast.makeText(this, "주문할 상품이 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        renderLines();

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        findViewById(R.id.btnChangeAddress).setOnClickListener(v -> chooseAddress());
        findViewById(R.id.btnSearchAddress).setOnClickListener(v ->
                PostcodeDialog.show(this, (zip, address) -> {
                    etZipCode.setText(zip);
                    etAddress.setText(address);
                    etAddressDetail.setText("");
                    etAddressDetail.requestFocus();
                }));
        findViewById(R.id.btnManagePayment).setOnClickListener(v ->
                startActivity(new Intent(this, PaymentMethodActivity.class)));
        findViewById(R.id.btnCoupon).setOnClickListener(v -> chooseCoupon());

        // 주문하기
        btnOrder.setOnClickListener(v -> order());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (orderCompleted || lines.isEmpty()) {
            return;
        }

        if (!LoginSession.isLoggedIn(this)) {
            askLogin();
            return;
        }

        if (!addressFilled) {
            MyPageStore.Address address = MyPageStore.getDefaultAddress();
            if (address != null) {
                fillAddress(address);
            } else {
                fillFromAccount();
            }
            addressFilled = true;
        }

        renderPayments();
        updatePrices();
    }

    private void askLogin() {
        if (loginAsked) {
            finish();
            return;
        }
        loginAsked = true;
        new AlertDialog.Builder(this)
                .setTitle("로그인이 필요해요")
                .setMessage("주문하려면 로그인해 주세요. 로그인하면 이 화면으로 돌아와 주문을 이어서 할 수 있어요.")
                .setCancelable(false)
                .setPositiveButton("로그인", (d, w) ->
                        startActivity(LoginMainActivity.returnIntent(this)))
                .setNegativeButton("취소", (d, w) -> finish())
                .show();
    }

    private void loadLines() {
        cartIds = getIntent().getLongArrayExtra(EXTRA_CART_IDS);

        if (cartIds != null) {
            for (long id : cartIds) {
                ShopStore.CartItem item = ShopStore.find(id);
                if (item != null) {
                    lines.add(item);
                }
            }
            return;
        }

        int productId = getIntent().getIntExtra(EXTRA_BUY_PRODUCT, -1);
        String serverId = getIntent().getStringExtra(EXTRA_BUY_SERVER);
        Product product = null;

        if (serverId != null && !serverId.isEmpty()) {
            product = RecommendApi.serverProduct(serverId);
        }
        if (product == null) {
            product = DummyData.getProductById(productId);
        }
        if (product == null) {
            return;
        }

        lines.add(ShopStore.newItem(
                product,
                getIntent().getStringExtra(EXTRA_BUY_SIZE),
                getIntent().getStringExtra(EXTRA_BUY_COLOR),
                getIntent().getIntExtra(EXTRA_BUY_QUANTITY, 1)
        ));
    }

    private void renderLines() {
        containerOrderItems.removeAllViews();
        productTotal = 0;
        int count = 0;

        for (ShopStore.CartItem item : lines) {
            productTotal += item.totalPrice();
            count += item.quantity;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(12));

            TextView brand = text(item.brand, 12, false, 0xFF888888);
            TextView name = text(item.name, 16, true, 0xFF222222);
            TextView option = text("옵션: " + item.optionText() + "  ·  수량 " + item.quantity + "개",
                    14, false, 0xFF777777);
            TextView price = text(MyPageStore.formatPrice(item.totalPrice()), 16, true, 0xFF111111);

            row.addView(brand);
            row.addView(name);
            row.addView(option);
            row.addView(price);

            if (containerOrderItems.getChildCount() > 0) {
                View line = new View(this);
                line.setBackgroundColor(0xFFEEEEEE);
                containerOrderItems.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
            containerOrderItems.addView(row);
        }

        tvOrderItemsTitle.setText("주문 상품 " + lines.size() + "종 (총 " + count + "개)");
    }

    private void fillFromAccount() {
        MyPageStore.User user = MyPageStore.getUser();
        etReceiver.setText(user.name);
        etPhone.setText(user.phone);
        tvAddressLabel.setText("저장된 배송지가 없어요. 입력한 배송지를 저장할 수 있어요.");
        cbSaveAddress.setChecked(true);
        cbSaveAddress.setVisibility(View.VISIBLE);
    }

    private void fillAddress(MyPageStore.Address address) {
        etReceiver.setText(address.receiver);
        etPhone.setText(address.phone);
        etZipCode.setText(address.zipCode);
        etAddress.setText(address.address1);
        etAddressDetail.setText(address.address2);
        etRequest.setText(address.request);
        tvAddressLabel.setText((address.isDefault ? "기본 배송지 · " : "") + address.label);
        cbSaveAddress.setChecked(false);
        cbSaveAddress.setVisibility(View.GONE);
    }

    private void clearAddress() {
        etReceiver.setText(MyPageStore.getUser().name);
        etPhone.setText(MyPageStore.getUser().phone);
        etZipCode.setText("");
        etAddress.setText("");
        etAddressDetail.setText("");
        etRequest.setText("");
        tvAddressLabel.setText("새 배송지");
        cbSaveAddress.setVisibility(View.VISIBLE);
        cbSaveAddress.setChecked(true);
    }

    private void chooseAddress() {
        List<MyPageStore.Address> addresses = MyPageStore.getAddresses();
        List<String> items = new ArrayList<>();

        for (MyPageStore.Address address : addresses) {
            items.add((address.isDefault ? "[기본] " : "") + address.label + "\n"
                    + address.receiver + " · " + address.getFullAddress());
        }
        items.add("+ 새 배송지 직접 입력");
        items.add("배송지 관리로 이동");

        new AlertDialog.Builder(this)
                .setTitle("배송지 선택")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < addresses.size()) {
                        fillAddress(addresses.get(which));
                    } else if (which == addresses.size()) {
                        clearAddress();
                    } else {
                        addressFilled = false;
                        startActivity(new Intent(this, DeliveryAddressActivity.class));
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void renderPayments() {
        String previous = selectedPayment();
        rgPayment.removeAllViews();
        paymentLabels.clear();

        for (MyPageStore.PaymentCard card : MyPageStore.getCards()) {
            addPayment(card.company + " " + card.getMaskedNumber()
                    + (card.isDefault ? " (기본)" : ""));
        }
        if (MyPageStore.isKakaoPayLinked()) {
            addPayment("카카오페이");
        }
        if (MyPageStore.isNaverPayLinked()) {
            addPayment("네이버페이");
        }
        addPayment(PAY_OTHER_CARD);
        addPayment(PAY_TRANSFER);

        int checkIndex = 0;
        if (previous != null && paymentLabels.contains(previous)) {
            checkIndex = paymentLabels.indexOf(previous);
        } else {
            List<MyPageStore.PaymentCard> cards = MyPageStore.getCards();
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).isDefault) {
                    checkIndex = i;
                }
            }
        }
        ((RadioButton) rgPayment.getChildAt(checkIndex)).setChecked(true);
    }

    private void addPayment(String label) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(0xFF111111);
        button.setButtonTintList(ColorStateList.valueOf(0xFF111111));
        button.setPadding(dp(4), dp(6), 0, dp(6));
        rgPayment.addView(button);
        paymentLabels.add(label);
    }

    private String selectedPayment() {
        int id = rgPayment.getCheckedRadioButtonId();
        if (id == -1) {
            return null;
        }
        View view = rgPayment.findViewById(id);
        int index = rgPayment.indexOfChild(view);
        return index >= 0 && index < paymentLabels.size() ? paymentLabels.get(index) : null;
    }

    private void chooseCoupon() {
        List<Coupons.Coupon> usable = Coupons.usable(productTotal);

        if (usable.isEmpty()) {
            Toast.makeText(this,
                    "사용할 수 있는 쿠폰이 없어요. 마이페이지 > 쿠폰함에서 쿠폰을 받을 수 있어요.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        List<String> items = new ArrayList<>();
        items.add("사용 안 함");
        for (Coupons.Coupon coupon : usable) {
            items.add(coupon.name + " (-" + MyPageStore.formatPrice(coupon.discountFor(productTotal)) + ")");
        }

        new AlertDialog.Builder(this)
                .setTitle("쿠폰 선택")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    selectedCoupon = which == 0 ? null : usable.get(which - 1);
                    updatePrices();
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void updatePrices() {
        discount = selectedCoupon == null ? 0 : selectedCoupon.discountFor(productTotal);
        if (selectedCoupon != null && discount == 0) {
            selectedCoupon = null;
        }
        shippingFee = ShopStore.shippingFee(productTotal);
        int total = productTotal - discount + shippingFee;

        tvOrderPrice.setText(MyPageStore.formatPrice(productTotal));
        tvDiscountPrice.setText(discount == 0 ? "0원" : "-" + MyPageStore.formatPrice(discount));
        tvDeliveryPrice.setText(shippingFee == 0 ? "무료"
                : MyPageStore.formatPrice(shippingFee) + " ("
                + MyPageStore.formatPrice(ShopStore.FREE_SHIPPING_MIN) + " 이상 무료)");
        tvTotalPrice.setText(MyPageStore.formatPrice(total));
        tvCoupon.setText(selectedCoupon == null ? "사용 안 함"
                : selectedCoupon.name + " (-" + MyPageStore.formatPrice(discount) + ")");

        // 결제 버튼
        btnOrder.setText(MyPageStore.formatPrice(total) + " 결제하기");
    }

    private void order() {

        if (orderCompleted) {
            return;
        }

        if (!LoginSession.isLoggedIn(this)) {
            loginAsked = false;
            askLogin();
            return;
        }

        String receiver = etReceiver.getText().toString().trim();
        String phone = InputRules.normalizePhone(etPhone.getText().toString());
        String zipCode = etZipCode.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String addressDetail = etAddressDetail.getText().toString().trim();

        // 받는 사람
        if (receiver.isEmpty()) {
            etReceiver.setError("받는 사람을 입력해주세요.");
            etReceiver.requestFocus();
            return;
        }

        // 전화번호
        if (phone == null) {
            etPhone.setError("휴대폰 번호를 정확히 입력해주세요.");
            etPhone.requestFocus();
            return;
        }
        etPhone.setText(phone);

        if (!zipCode.matches("[0-9]{5}")) {
            etZipCode.setError("우편번호 5자리를 입력해주세요.");
            etZipCode.requestFocus();
            return;
        }

        // 주소
        if (address.isEmpty()) {
            etAddress.setError("주소를 입력해주세요.");
            etAddress.requestFocus();
            return;
        }

        // 상세주소
        if (addressDetail.isEmpty()) {
            etAddressDetail.setError("상세 주소를 입력해주세요.");
            etAddressDetail.requestFocus();
            return;
        }

        // 결제 방법
        String payment = selectedPayment();

        if (payment == null) {
            Toast.makeText(
                    this,
                    "결제 방법을 선택해주세요.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 결제 직전 사이즈 주의 팝업
        List<FitResult> warnings = SizeFitChecker.checkItems(lines);

        if (!warnings.isEmpty()) {

            SizeWarningDialog.show(
                    OrderActivity.this,
                    warnings,
                    SizeWarningDialog.Action.PAYMENT,
                    () -> confirmPayment(payment)
            );

            return;
        }

        confirmPayment(payment);
    }

    private void confirmPayment(String payment) {
        int total = productTotal - discount + shippingFee;
        String message = "상품 " + lines.size() + "종 · " + MyPageStore.formatPrice(total)
                + "\n결제 수단: " + payment
                + (selectedCoupon == null ? "" : "\n쿠폰: " + selectedCoupon.name)
                + "\n\n결제할까요?";

        AppDialog.show(this, false, "결제 확인", message,
                "취소", null,
                "결제하기", () -> completeOrder(payment));
    }

    // 결제 완료 및 주문 내역 저장
    private void completeOrder(String payment) {

        if (orderCompleted) {
            return;
        }

        orderCompleted = true;
        btnOrder.setEnabled(false);

        String groupNo = "ORD" + System.currentTimeMillis();

        String orderDate = MyPageStore.today();

        String receiver = etReceiver.getText().toString().trim();
        String phone = InputRules.normalizePhone(etPhone.getText().toString());
        String zipCode = etZipCode.getText().toString().trim();
        String address = etAddress.getText().toString().trim() + " "
                + etAddressDetail.getText().toString().trim();
        String request = etRequest.getText().toString().trim();

        List<MyPageStore.Order> newOrders = new ArrayList<>();
        int remainingDiscount = discount;
        long now = System.currentTimeMillis();

        for (int i = 0; i < lines.size(); i++) {
            ShopStore.CartItem item = lines.get(i);

            int lineDiscount;
            if (i == lines.size() - 1) {
                lineDiscount = remainingDiscount;
            } else {
                lineDiscount = productTotal == 0 ? 0
                        : (int) ((long) discount * item.totalPrice() / productTotal);
                remainingDiscount -= lineDiscount;
            }

            MyPageStore.Order order = new MyPageStore.Order(
                    groupNo + "-" + (i + 1),
                    orderDate,
                    item.brand,
                    item.name,
                    item.optionText(),
                    item.quantity,
                    item.price,
                    MyPageStore.STATUS_PAID,
                    "",
                    ""
            );
            order.groupNo = groupNo;
            order.productId = item.productId;
            order.serverId = item.serverId;
            order.size = item.size;
            order.color = item.color;
            order.receiver = receiver;
            order.receiverPhone = phone == null ? "" : phone;
            order.zipCode = zipCode;
            order.address = address;
            order.request = request;
            order.paymentMethod = payment;
            order.discount = lineDiscount;
            order.shippingFee = i == 0 ? shippingFee : 0;
            order.createdAt = now;
            newOrders.add(order);
        }

        // 주문 저장
        MyPageStore.addOrders(newOrders);

        if (selectedCoupon != null) {
            MyPageStore.useCoupon(selectedCoupon.id);
        }

        if (cbSaveAddress.getVisibility() == View.VISIBLE && cbSaveAddress.isChecked()) {
            MyPageStore.saveAddress(new MyPageStore.Address(
                    MyPageStore.newId(),
                    MyPageStore.getAddresses().isEmpty() ? "기본 배송지" : "배송지",
                    receiver,
                    phone == null ? "" : phone,
                    zipCode,
                    etAddress.getText().toString().trim(),
                    etAddressDetail.getText().toString().trim(),
                    request,
                    MyPageStore.getAddresses().isEmpty()
            ));
        }

        if (cartIds != null) {
            ShopStore.removeIds(cartIds);
        }

        // 구매는 추천 학습에 반영 (장바구니의 서버 상품마다 purchase, 반품 때 쓰려고 주문번호와 함께 저장)
        for (MyPageStore.Order order : newOrders) {
            RecommendApi.recordPurchase(this, order.orderNo, order.serverId);
        }

        // 주문 완료 화면으로 이동
        Intent intent = new Intent(
                OrderActivity.this,
                OrderCompleteActivity.class
        );

        intent.putExtra("totalPrice", productTotal - discount + shippingFee);
        intent.putExtra("orderNo", groupNo);

        startActivity(intent);
        finish();
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
