package com.example.capstone.login;

import com.example.capstone.R;
import com.example.capstone.data.CheckoutCoupons;
import com.example.capstone.data.MyPageStore;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class CouponHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coupon_history);

        ((TextView) findViewById(R.id.tvToolbarTitle)).setText("완료 / 만료 쿠폰");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.containerFinished);
        List<CheckoutCoupons.Coupon> coupons = CheckoutCoupons.finished();

        for (CheckoutCoupons.Coupon coupon : coupons) {
            View card = CouponActivity.couponCard(this, container, coupon);
            String state = MyPageStore.isCouponUsed(coupon.id)
                    ? "사용완료 · " + MyPageStore.couponUsedDate(coupon.id)
                    : "기간만료 · " + coupon.expireText();
            ((TextView) card.findViewById(R.id.tvCouponExpire)).setText(state);
            card.setAlpha(0.5f);
            container.addView(card);
        }

        findViewById(R.id.tvFinishedEmpty).setVisibility(coupons.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
