package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.mypage.OrderDetailActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderCompleteActivity extends AppCompatActivity {

    /** true면 계좌이체: "주문이 완료되었습니다" 대신 이체할 계좌 안내를 보여 준다 */
    public static final String EXTRA_BANK_TRANSFER = "bank_transfer";

    // 시연용 입금 계좌 (실제 계좌가 정해지면 바꿀 것)
    private static final String BANK_ACCOUNT = "100000-01-001001";
    private static final String BANK_HOLDER = "홍길동";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_complete);

        TextView tvCompletePrice = findViewById(R.id.tvCompletePrice);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        int totalPrice = getIntent().getIntExtra("totalPrice", 0);

        String formattedPrice =
                NumberFormat
                        .getNumberInstance(Locale.KOREA)
                        .format(totalPrice)
                        + "원";

        if (getIntent().getBooleanExtra(EXTRA_BANK_TRANSFER, false)) {
            // 제목 "주문이 완료되었습니다"(굵게)는 그대로 두고 아래에 이체할 계좌를 보여 준다
            tvCompletePrice.setTextColor(0xFF111111);
            tvCompletePrice.setText(
                    "다음 계좌정보로 이체해주시면 결제가 완료돼요.\n"
                            + "계좌번호정보: " + MaskText.account(BANK_ACCOUNT) + "\n"
                            + "예금주: " + MaskText.holder(BANK_HOLDER) + "\n"
                            + "금액: " + formattedPrice
            );
        } else {
            tvCompletePrice.setText(
                    "총 결제금액 " + formattedPrice
            );
        }

        showSummary(getIntent().getStringExtra("orderNo"));

        btnGoHome.setOnClickListener(v -> {

            Intent intent = new Intent(
                    OrderCompleteActivity.this,
                    MainActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);
            finish();
        });
    }

    /** 방금 한 주문의 주문번호·상품·배송지를 보여 주고, 주문 상세로 가는 버튼을 켠다 (주문을 못 찾으면 그대로 숨김) */
    private void showSummary(String groupNo) {
        if (groupNo == null) {
            return;
        }
        List<MyPageStore.Order> items = new ArrayList<>();
        for (MyPageStore.Order order : MyPageStore.getOrders(MyPageStore.STATUS_ALL)) {
            if (groupNo.equals(order.getGroupNo())) {
                items.add(order);
            }
        }
        if (items.isEmpty()) {
            return;
        }

        StringBuilder names = new StringBuilder();
        for (MyPageStore.Order order : items) {
            if (names.length() > 0) {
                names.append('\n');
            }
            names.append(order.productName);
            if (order.option != null && !order.option.isEmpty()) {
                names.append(" (").append(order.option).append(')');
            }
            names.append(" × ").append(order.quantity);
        }

        MyPageStore.Order first = items.get(0);
        String address = first.shippingAddress == null ? "" : first.shippingAddress;
        if (first.receiver != null && !first.receiver.isEmpty()) {
            address = first.receiver + "\n" + address;
        }

        ((TextView) findViewById(R.id.tvSummaryOrderNo)).setText(groupNo);
        ((TextView) findViewById(R.id.tvSummaryItems)).setText(names.toString());
        ((TextView) findViewById(R.id.tvSummaryAddress)).setText(address.isEmpty() ? "-" : address);
        findViewById(R.id.layoutOrderSummary).setVisibility(View.VISIBLE);

        Button btnDetail = findViewById(R.id.btnOrderDetail);
        btnDetail.setVisibility(View.VISIBLE);
        btnDetail.setOnClickListener(v -> {
            startActivity(OrderDetailActivity.newIntent(this, groupNo));
            finish();
        });
    }
}