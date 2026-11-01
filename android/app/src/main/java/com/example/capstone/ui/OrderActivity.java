package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.SizeFitChecker;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class OrderActivity extends AppCompatActivity {

    private TextView btnBack;
    private TextView tvProductName;
    private TextView tvProductOption;
    private TextView tvProductQuantity;
    private TextView tvProductPrice;
    private TextView tvOrderPrice;
    private TextView tvTotalPrice;

    private EditText etReceiver;
    private EditText etPhone;
    private EditText etAddress;
    private EditText etAddressDetail;

    private RadioGroup rgPayment;
    private Button btnOrder;

    private String productNames;
    private int totalQuantity;
    private int totalPrice;

    private boolean orderCompleted = false;

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

        etReceiver = findViewById(R.id.etReceiver);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etAddressDetail = findViewById(R.id.etAddressDetail);

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

        // 선택 옵션
        // 현재 장바구니에서 별도 옵션을 전달하지 않으면 기본 문구 표시
        String option = getIntent().getStringExtra("productOption");

        if (option == null || option.trim().isEmpty()) {
            option = "선택 옵션";
        }

        tvProductOption.setText(option);

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
        tvTotalPrice.setText(formattedPrice);

        // 결제 버튼
        btnOrder.setText(formattedPrice + " 결제하기");

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 주문하기
        btnOrder.setOnClickListener(v -> order());
    }

    private void order() {

        if (orderCompleted) {
            return;
        }

        String receiver = etReceiver.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String addressDetail = etAddressDetail.getText().toString().trim();

        // 받는 사람
        if (receiver.isEmpty()) {
            etReceiver.setError("받는 사람을 입력해주세요.");
            etReceiver.requestFocus();
            return;
        }

        // 전화번호
        if (phone.isEmpty()) {
            etPhone.setError("전화번호를 입력해주세요.");
            etPhone.requestFocus();
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
        int checkedPaymentId = rgPayment.getCheckedRadioButtonId();

        if (checkedPaymentId == -1) {
            Toast.makeText(
                    this,
                    "결제 방법을 선택해주세요.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 결제 직전 사이즈 주의 팝업
        List<FitResult> warnings = SizeFitChecker.checkCart();

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

    // 결제 완료 및 주문 내역 저장
    private void completeOrder() {

        if (orderCompleted) {
            return;
        }

        orderCompleted = true;
        btnOrder.setEnabled(false);

        String orderNo = "ORD" + System.currentTimeMillis();

        String orderDate = MyPageStore.today();

        String option = getIntent().getStringExtra("productOption");

        if (option == null || option.trim().isEmpty()) {
            option = "선택 옵션";
        }

        // 현재 장바구니는 상품별 정보가 아닌
        // 상품명 목록, 총수량, 총금액을 전달한다.
        // 총 결제금액이 정확하도록 주문 한 건으로 저장한다.
        MyPageStore.Order newOrder =
                new MyPageStore.Order(
                        orderNo,
                        orderDate,
                        "쇼핑몰",
                        productNames,
                        option + " / 총 " + Math.max(1, totalQuantity) + "개",
                        1,
                        totalPrice,
                        MyPageStore.STATUS_PAID,
                        "",
                        ""
                );

        // 주문 저장
        MyPageStore.addOrder(newOrder);

        // 구매는 추천 학습에 반영 (장바구니의 서버 상품마다 purchase, 반품 때 쓰려고 주문번호와 함께 저장)
        RecommendApi.recordPurchase(this, orderNo);

        // 주문 완료 화면으로 이동
        Intent intent = new Intent(
                OrderActivity.this,
                OrderCompleteActivity.class
        );

        intent.putExtra("totalPrice", totalPrice);
        intent.putExtra("orderNo", orderNo);

        startActivity(intent);
        finish();
    }
}
