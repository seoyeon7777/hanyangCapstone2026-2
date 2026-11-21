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
import com.example.capstone.data.DummyData;
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
    public static final String EXTRA_BRAND = "product_brand";
    public static final String EXTRA_PRODUCT_ID = "order_product_id";

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
            "선택 안 함", "문 앞에 놓아 주십시오", "경비실에 맡겨 주십시오",
            "택배함에 넣어 주십시오", "배송 전 연락 바랍니다", "부재 시 문 앞에 놓아 주십시오",
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

    // 카드 결제 화면(CardPaymentActivity)에서 "결제"까지 마치고 돌아오면 주문을 저장한다
    private final ActivityResultLauncher<Intent> cardPayment = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    completeOrder(false,
                            data == null ? "" : data.getStringExtra(CardPaymentActivity.RESULT_CARD),
                            data == null ? "" : data.getStringExtra(CardPaymentActivity.RESULT_INSTALLMENT));
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
        boolean usable = coupon.discountFor(couponLines()) > 0;

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

        List<CheckoutCoupons.Line> lines = couponLines();
        int discount = coupon.discountFor(lines);
        boolean usable = discount > 0;
        // 쓸 수 없는 이유: 쿠폰을 쓸 수 있는 상품이 없을 때와 최소 주문 금액에 못 미칠 때를 나눠 알려 준다
        String unusableReason = coupon.appliesToAny(lines)
                ? "이번 주문에는 사용할 수 없습니다"
                : "이 주문 상품에는 쓸 수 없습니다";

        ((TextView) card.findViewById(R.id.tvCouponDiscount)).setText(coupon.discountText());
        ((TextView) card.findViewById(R.id.tvCouponName)).setText(coupon.name);
        ((TextView) card.findViewById(R.id.tvCouponCondition)).setText(coupon.conditionText
                + (coupon.scopeText.isEmpty() ? "" : "\n" + coupon.scopeText + "에만 사용")
                + "\n" + (usable ? "이번 주문 할인 -" + won(discount) : unusableReason));
        ((TextView) card.findViewById(R.id.tvCouponExpire)).setText(coupon.expireText());
        return card;
    }

    private int couponDiscount() {
        return selectedCoupon == null ? 0 : selectedCoupon.discountFor(couponLines());
    }

    /** 쿠폰 적용 범위를 따질 주문 상품 목록 (신상·상의 같은 분류는 상품 목록에서 찾는다) */
    private List<CheckoutCoupons.Line> couponLines() {
        List<CheckoutCoupons.Line> result = new ArrayList<>();
        for (OrderLine line : orderLines()) {
            Product product = DummyData.getProductById(line.productId);
            result.add(new CheckoutCoupons.Line(
                    line.name,
                    product == null ? "" : product.getCategory(),
                    DummyData.isNew(product),
                    line.unitPrice * line.quantity));
        }
        return result;
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

        pay();
    }

    /** 결제 방법에 따라: 카드 → 카드 결제 화면 / 계좌이체 → 주문 저장 후 이체 안내 화면 */
    private void pay() {
        if (rgPayment.getCheckedRadioButtonId() == R.id.rbCard) {
            Intent intent = new Intent(this, CardPaymentActivity.class);
            intent.putExtra(CardPaymentActivity.EXTRA_AMOUNT, payPrice());
            cardPayment.launch(intent);
            return;
        }
        completeOrder(true, "", "");
    }

    /** 이번에 주문하는 장바구니 줄 (바로 구매면 빈 목록) */
    private List<Product> orderedLines() {
        return CartStore.linesById(this, getIntent().getLongArrayExtra(EXTRA_CART_LINE_IDS));
    }

    private String orderOption() {
        String option = getIntent().getStringExtra("productOption");
        return option == null ? "" : option.trim();
    }

    private static class OrderLine {
        String brand;
        String name;
        String option;
        int quantity;
        int unitPrice;
        int productId = -1;
        String serverId;
    }

    private List<OrderLine> orderLines() {
        List<OrderLine> lines = new ArrayList<>();

        for (Product product : orderedLines()) {
            if (product.getCartQuantity() <= 0) {
                continue;
            }
            OrderLine line = new OrderLine();
            line.brand = product.getBrand() == null ? "" : product.getBrand();
            line.name = product.getName();
            line.option = product.getOptionLabel();
            line.quantity = product.getCartQuantity();
            line.unitPrice = product.getPrice();
            line.productId = product.getId();
            line.serverId = product.isFromServer() ? product.getServerId() : null;
            lines.add(line);
        }

        if (lines.isEmpty()) {
            OrderLine line = new OrderLine();
            String brand = getIntent().getStringExtra(EXTRA_BRAND);
            line.brand = brand == null ? "" : brand;
            line.name = productNames;
            line.option = orderOption();
            line.quantity = Math.max(1, totalQuantity);
            line.unitPrice = totalPrice / line.quantity;
            line.productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
            line.serverId = getIntent().getStringExtra(EXTRA_BUY_NOW_SERVER_ID);
            lines.add(line);
        }
        return lines;
    }

    private String deliveryRequest() {
        int requestPosition = spOrderRequest.getSelectedItemPosition();
        if (requestPosition == DELIVERY_REQUESTS.length - 1) {
            return etOrderRequest.getText().toString().trim();
        }
        return requestPosition <= 0 ? "" : spOrderRequest.getSelectedItem().toString();
    }

    // 결제 완료 및 주문 내역 저장 (bankTransfer = 계좌이체면 완료 화면에 이체할 계좌를 보여 준다)
    private void completeOrder(boolean bankTransfer, String cardCompany, String installment) {

        if (orderCompleted) {
            return;
        }

        orderCompleted = true;
        btnOrder.setEnabled(false);

        String groupNo = "ORD" + System.currentTimeMillis();
        String orderDate = MyPageStore.today();
        MyPageStore.Address address = selectedAddress();
        String request = deliveryRequest();

        List<OrderLine> lines = orderLines();
        int linesTotal = 0;
        for (OrderLine line : lines) {
            linesTotal += line.unitPrice * line.quantity;
        }

        int discount = totalPrice - payPrice();
        int discountLeft = discount;

        List<MyPageStore.Order> newOrders = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            OrderLine line = lines.get(i);
            int lineTotal = line.unitPrice * line.quantity;
            int lineDiscount = i == lines.size() - 1
                    ? discountLeft
                    : (linesTotal == 0 ? 0 : (int) ((long) discount * lineTotal / linesTotal));
            discountLeft -= lineDiscount;

            MyPageStore.Order order = new MyPageStore.Order(
                    lines.size() == 1 ? groupNo : groupNo + "-" + (i + 1),
                    orderDate,
                    line.brand,
                    line.name,
                    line.option,
                    line.quantity,
                    line.unitPrice,
                    bankTransfer ? MyPageStore.STATUS_PENDING : MyPageStore.STATUS_PAID,
                    "",
                    ""
            );
            order.groupNo = groupNo;
            order.paidAmount = Math.max(0, lineTotal - lineDiscount);
            order.payMethod = bankTransfer ? MyPageStore.PAY_BANK : MyPageStore.PAY_CARD;
            order.cardCompany = cardCompany == null ? "" : cardCompany;
            order.installment = installment == null ? "" : installment;
            if (address != null) {
                order.receiver = address.receiver;
                order.receiverPhone = address.phone;
                order.shippingAddress = address.getFullAddress();
            }
            order.deliveryRequest = request;
            order.couponName = selectedCoupon == null ? "" : selectedCoupon.name;
            order.productId = line.productId;
            order.serverProductId = line.serverId == null ? "" : line.serverId;
            newOrders.add(order);

            if (line.serverId != null && !line.serverId.isEmpty()) {
                List<String> serverIds = new ArrayList<>();
                serverIds.add(line.serverId);
                RecommendApi.recordPurchase(this, order.orderNo, serverIds);
            }
        }

        MyPageStore.addOrders(newOrders);
        if (selectedCoupon != null) {
            MyPageStore.useCoupon(selectedCoupon.id, groupNo);
        }
        String orderNo = groupNo;

        // 결제한 줄만 장바구니에서 뺀다 (체크했지만 이번에 주문하지 않은 줄·바로 구매는 그대로)
        CartStore.removeLines(this, getIntent().getLongArrayExtra(EXTRA_CART_LINE_IDS));

        // 주문 완료 화면으로 이동
        Intent intent = new Intent(
                OrderActivity.this,
                OrderCompleteActivity.class
        );

        intent.putExtra("totalPrice", payPrice());
        intent.putExtra("orderNo", orderNo);
        intent.putExtra(OrderCompleteActivity.EXTRA_BANK_TRANSFER, bankTransfer);

        startActivity(intent);
        finish();
    }
}
