package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

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