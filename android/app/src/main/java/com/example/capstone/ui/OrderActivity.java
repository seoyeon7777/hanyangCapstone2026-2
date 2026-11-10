package com.example.capstone.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.example.capstone.R;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.Product;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.SizeFitChecker;
import com.example.capstone.data.CheckoutCoupons;
import com.example.capstone.mypage.DeliveryAddressActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderActivity extends AppCompatActivity {

    /** 장바구니에서 주문할 때: 주문하는 장바구니 줄 번호 (long[]). 결제를 마치면 이 줄만 장바구니에서 뺀다 */
    public static final String EXTRA_CART_LINE_IDS = "cart_line_ids";
    /** 상세 화면 "바로 구매"일 때: 그 상품의 서버 번호 (서버 상품만). 장바구니는 건드리지 않는다 */
    public static final String EXTRA_BUY_NOW_SERVER_ID = "buy_now_server_id";

    private View btnBack;
    private TextView tvProductName;
    private TextView tvProductOption;
    private TextView tvProductQuantity;
    private TextView tvProductPrice;
    private TextView tvOrderPrice;
    private TextView tvTotalPrice;

    private FrameLayout containerAddress;
    private View btnAddAddress;
    private TextView btnChangeAddress;
    private TextView tvCouponSelected;
    private TextView tvCouponDiscount;
    private FrameLayout containerCoupon;

    private RadioGroup rgPayment;
    private Button btnOrder;

    private String productNames;
    private int totalQuantity;
    private int totalPrice;

    private boolean orderCompleted = false;

    private Spinner spOrderRequest;
    private static final String[] DELIVERY_REQUESTS = {
            "선택 안 함", "문 앞에 놓아주세요", "경비실에 맡겨주세요",
            "택배함에 넣어주세요", "배송 전 연락 바랍니다", "부재 시 문 앞에 놓아주세요",
            "직접 입력"
    };
    private EditText etOrderRequest;
    private long selectedAddressId = -1;
    private CheckoutCoupons.Coupon selectedCoupon;

    private final ActivityResultLauncher<Intent> pickAddress = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedAddressId = result.getData()
                            .getLongExtra(DeliveryAddressActivity.EXTRA_ADDRESS_ID, -1);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_order);

        // 주문 및 리뷰 저장소 초기화
        MyPageStore.init(this);

        // XML 연결
        btnBack = findViewById(R.id.btnBack);

        tvProductName = findViewById(R.id.tvProductName);
        tvProductOption = findViewById(R.id.tvProductOption);
        tvProductQuantity = findViewById(R.id.tvProductQuantity);
        tvProductPrice = findViewById(R.id.tvProductPrice);

        tvOrderPrice = findViewById(R.id.tvOrderPrice);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);

        spOrderRequest = findViewById(R.id.spOrderRequest);
        ArrayAdapter<String> requestAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, DELIVERY_REQUESTS);
        requestAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spOrderRequest.setAdapter(requestAdapter);
        etOrderRequest = findViewById(R.id.etOrderRequest);
        // "직접 입력"을 고르면 입력칸이 나타난다
        spOrderRequest.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                etOrderRequest.setVisibility(position == DELIVERY_REQUESTS.length - 1 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        containerAddress = findViewById(R.id.containerAddress);
        btnAddAddress = findViewById(R.id.btnAddAddress);
        btnChangeAddress = findViewById(R.id.btnChangeAddress);
        tvCouponSelected = findViewById(R.id.tvCouponSelected);
        tvCouponDiscount = findViewById(R.id.tvCouponDiscount);
        containerCoupon = findViewById(R.id.containerCoupon);

        rgPayment = findViewById(R.id.rgPayment);
        btnOrder = findViewById(R.id.btnOrder);

        // 장바구니에서 전달한 주문 정보
        productNames = getIntent().getStringExtra("productNames");
        totalQuantity = getIntent().getIntExtra("totalQuantity", 0);
        totalPrice = getIntent().getIntExtra("totalPrice", 0);

        if (productNames == null || productNames.trim().isEmpty()) {
            productNames = "주문 상품";
        }

        // 상품명
        tvProductName.setText(productNames);

        // 장바구니에서 고른 옵션 그대로. 옵션이 없는 상품이면 줄을 숨긴다
        String option = orderOption();

        tvProductOption.setText(option);
        tvProductOption.setVisibility(option.isEmpty() ? View.GONE : View.VISIBLE);

        // 수량
        tvProductQuantity.setText(
                "총 수량: " + totalQuantity + "개"
        );

        // 금액
        String formattedPrice = NumberFormat
                .getNumberInstance(Locale.KOREA)
                .format(totalPrice) + "원";

        tvProductPrice.setText(formattedPrice);
        tvOrderPrice.setText(formattedPrice);
        updatePrices();

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        btnChangeAddress.setOnClickListener(v -> openAddressBook());
        btnAddAddress.setOnClickListener(v -> openAddressBook());
        findViewById(R.id.btnSelectCoupon).setOnClickListener(v -> showCouponPicker());

        // 주문하기
        btnOrder.setOnClickListener(v -> order());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAddress();
    }

    private MyPageStore.Address selectedAddress() {
        List<MyPageStore.Address> addresses = MyPageStore.getAddresses();
        if (addresses.isEmpty()) {
            return null;
        }
        MyPageStore.Address found = MyPageStore.findAddress(selectedAddressId);
        return found != null ? found : addresses.get(0);
    }

    private void renderAddress() {
        MyPageStore.Address address = selectedAddress();

        containerAddress.removeAllViews();
        btnAddAddress.setVisibility(address == null ? View.VISIBLE : View.GONE);
        btnChangeAddress.setVisibility(address == null ? View.GONE : View.VISIBLE);

        if (address != null) {
            selectedAddressId = address.id;
            containerAddress.addView(addressCard(containerAddress, address));
        }
    }

    private View addressCard(ViewGroup parent, MyPageStore.Address address) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_address, parent, false);

        ((TextView) card.findViewById(R.id.tvLabel)).setText(address.label);
        ((TextView) card.findViewById(R.id.tvReceiver)).setText(address.receiver + " · " + com.example.capstone.data.LocalAccounts.formatPhone(address.phone));
        ((TextView) card.findViewById(R.id.tvAddress)).setText(address.getFullAddress());

        TextView tvRequest = card.findViewById(R.id.tvRequest);
        tvRequest.setText("");
        tvRequest.setVisibility(View.GONE);

        card.findViewById(R.id.tvDefaultBadge)
                .setVisibility(address.isDefault ? View.VISIBLE : View.GONE);
        ((View) card.findViewById(R.id.btnSetDefault).getParent()).setVisibility(View.GONE);
        return card;
    }

    private LinearLayout sheetList(String titleText) {
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * density);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(R.drawable.bg_dialog_card);
        list.setPadding(pad, pad, pad, Math.round(28 * density));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF111111);
        list.addView(title);
        return list;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        return lp;
    }

    private void openAddressBook() {
        Intent intent = new Intent(this, DeliveryAddressActivity.class);
        intent.putExtra(DeliveryAddressActivity.EXTRA_SELECT_MODE, true);
        intent.putExtra(DeliveryAddressActivity.EXTRA_ADDRESS_ID, selectedAddressId);
        pickAddress.launch(intent);
    }

    private void showCouponPicker() {
        List<CheckoutCoupons.Coupon> coupons = CheckoutCoupons.owned();

        if (coupons.isEmpty()) {
            Toast.makeText(this,
                    "사용할 수 있는 쿠폰이 없어요. 마이페이지 > 쿠폰함에서 받을 수 있어요.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout list = sheetList("쿠폰 선택");

        list.addView(noCouponRow(sheet), rowParams());

        for (CheckoutCoupons.Coupon coupon : coupons) {
            list.addView(couponCard(list, coupon, sheet), rowParams());
        }

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.addView(list);
        sheet.setContentView(scroll);
        sheet.show();
    }

    private View noCouponRow(BottomSheetDialog sheet) {
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);

        TextView row = new TextView(this);
        row.setText("쿠폰 사용 안 함");
        row.setTextSize(15);
        row.setTextColor(0xFF111111);
        row.setPadding(pad, pad, pad, pad);
        row.setBackgroundResource(selectedCoupon == null ? R.drawable.bg_card_active : R.drawable.bg_card);
        row.setOnClickListener(v -> {
            selectedCoupon = null;
            updatePrices();
            sheet.dismiss();
        });
        return row;
    }

    private View couponCard(ViewGroup parent, CheckoutCoupons.Coupon coupon, BottomSheetDialog sheet) {
        View card = couponDetail(parent, coupon);
        boolean usable = coupon.discountFor(totalPrice) > 0;

        if (coupon == selectedCoupon) {
            card.setBackgroundResource(R.drawable.bg_card_active);
        }
        card.setAlpha(usable ? 1f : 0.4f);

        if (usable) {
            card.setOnClickListener(v -> {
                selectedCoupon = coupon;
                updatePrices();
                sheet.dismiss();
            });
        }
        return card;
    }

    private View couponDetail(ViewGroup parent, CheckoutCoupons.Coupon coupon) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_coupon_owned, parent, false);

        int discount = coupon.discountFor(totalPrice);
        boolean usable = discount > 0;

        ((TextView) card.findViewById(R.id.tvCouponDiscount)).setText(coupon.discountText());
        ((TextView) card.findViewById(R.id.tvCouponName)).setText(coupon.name);
        ((TextView) card.findViewById(R.id.tvCouponCondition)).setText(coupon.conditionText
                + "\n" + (usable ? "이번 주문 할인 -" + won(discount) : "이번 주문에는 사용할 수 없어요"));
        ((TextView) card.findViewById(R.id.tvCouponExpire)).setText(coupon.expireText());
        return card;
    }

    private int couponDiscount() {
        return selectedCoupon == null ? 0 : selectedCoupon.discountFor(totalPrice);
    }

    private int payPrice() {
        return Math.max(0, totalPrice - couponDiscount());
    }

    private void updatePrices() {
        int discount = couponDiscount();

        tvCouponSelected.setText(selectedCoupon == null ? "사용 안 함" : "쿠폰 1장 적용");
        tvCouponSelected.setTextColor(selectedCoupon == null ? 0xFF777777 : 0xFF111111);
        tvCouponDiscount.setText(discount == 0 ? "0원" : "-" + won(discount));

        containerCoupon.removeAllViews();
        containerCoupon.setVisibility(selectedCoupon == null ? View.GONE : View.VISIBLE);
        if (selectedCoupon != null) {
            View card = couponDetail(containerCoupon, selectedCoupon);
            card.setBackgroundResource(R.drawable.bg_card_active);
            card.setOnClickListener(v -> showCouponPicker());
            containerCoupon.addView(card);
        }

        String pay = won(payPrice());
        tvTotalPrice.setText(pay);
        btnOrder.setText(pay + " 결제하기");
    }

    private static String won(int price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "원";
    }

    private void order() {

        if (orderCompleted) {
            return;
        }

        if (selectedAddress() == null) {
            Toast.makeText(this, "배송지를 등록해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 결제 방법
        int checkedPaymentId = rgPayment.getCheckedRadioButtonId();

        if (checkedPaymentId == -1) {
            Toast.makeText(
                    this,
                    "결제 방법을 선택해주세요.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 결제 직전 사이즈 주의 팝업 (주문하는 장바구니 줄만. 바로 구매는 상세 화면에서 이미 확인했다)
        List<FitResult> warnings = SizeFitChecker.checkLines(orderedLines());

        if (!warnings.isEmpty()) {

            SizeWarningDialog.show(
                    OrderActivity.this,
                    warnings,
                    SizeWarningDialog.Action.PAYMENT,
                    this::completeOrder
            );

            return;
        }

        completeOrder();
    }

    /** 이번에 주문하는 장바구니 줄 (바로 구매면 빈 목록) */
    private List<Product> orderedLines() {
        return CartStore.linesById(this, getIntent().getLongArrayExtra(EXTRA_CART_LINE_IDS));
    }

    private String orderOption() {
        String option = getIntent().getStringExtra("productOption");
        return option == null ? "" : option.trim();
    }

    // 결제 완료 및 주문 내역 저장
    private void completeOrder() {

        if (orderCompleted) {
            return;
        }

        orderCompleted = true;
        btnOrder.setEnabled(false);

        String orderNo = "ORD" + System.currentTimeMillis();

        String orderDate = MyPageStore.today();

        String option = orderOption();

        // 현재 장바구니는 상품별 정보가 아닌
        // 상품명 목록, 총수량, 총금액을 전달한다.
        // 총 결제금액이 정확하도록 주문 한 건으로 저장한다.
        MyPageStore.Order newOrder =
                new MyPageStore.Order(
                        orderNo,
                        orderDate,
                        "쇼핑몰",
                        productNames,
                        (option.isEmpty() ? "" : option + " / ")
                                + "총 " + Math.max(1, totalQuantity) + "개",
                        1,
                        payPrice(),
                        MyPageStore.STATUS_PAID,
                        "",
                        ""
                );

        // 주문 저장
        MyPageStore.addOrder(newOrder);
        // 주문별 요청사항 저장: 배송지 자체의 요청사항과 분리
        int requestPosition = spOrderRequest.getSelectedItemPosition();
        String deliveryRequest = requestPosition <= 0
                ? "" : spOrderRequest.getSelectedItem().toString();
        if (requestPosition == DELIVERY_REQUESTS.length - 1) {
            deliveryRequest = etOrderRequest.getText().toString().trim();
        }
        getSharedPreferences("order_delivery_requests", MODE_PRIVATE)
                .edit().putString(orderNo, deliveryRequest).apply();

        // 구매는 추천 학습에 반영 (주문한 서버 상품마다 purchase, 반품 때 쓰려고 주문번호와 함께 저장)
        List<String> serverIds = new ArrayList<>();
        for (Product line : orderedLines()) {
            if (line.isFromServer()) {
                serverIds.add(line.getServerId());
            }
        }
        String buyNowServerId = getIntent().getStringExtra(EXTRA_BUY_NOW_SERVER_ID);
        if (buyNowServerId != null && !buyNowServerId.isEmpty()) {
            serverIds.add(buyNowServerId);
        }
        RecommendApi.recordPurchase(this, orderNo, serverIds);

        // 결제한 줄만 장바구니에서 뺀다 (체크했지만 이번에 주문하지 않은 줄·바로 구매는 그대로)
        CartStore.removeLines(this, getIntent().getLongArrayExtra(EXTRA_CART_LINE_IDS));

        // 주문 완료 화면으로 이동
        Intent intent = new Intent(
                OrderActivity.this,
                OrderCompleteActivity.class
        );

        intent.putExtra("totalPrice", payPrice());
        intent.putExtra("orderNo", orderNo);

        startActivity(intent);
        finish();
    }
}
