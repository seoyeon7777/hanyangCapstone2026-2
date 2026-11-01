package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.mypage.OrderListActivity;

import java.text.NumberFormat;
import java.util.Locale;

public class OrderCompleteActivity extends AppCompatActivity {

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

        tvCompletePrice.setText(
                "총 결제금액 " + formattedPrice
        );

        String orderNo = getIntent().getStringExtra("orderNo");
        ((TextView) findViewById(R.id.tvCompleteOrderNo)).setText(
                orderNo == null ? "" : "주문번호 " + orderNo
                        + "\n배송 상태는 마이페이지 > 주문 내역에서 확인할 수 있어요."
        );

        findViewById(R.id.btnGoOrders).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivities(new Intent[]{
                    home,
                    OrderListActivity.newIntent(this, MyPageStore.STATUS_ALL)
            });
            finish();
        });

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
}