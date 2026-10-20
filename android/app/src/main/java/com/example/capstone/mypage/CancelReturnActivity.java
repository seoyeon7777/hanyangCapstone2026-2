package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Claim;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** 마이페이지 > 취소/반품/교환 내역 */
public class CancelReturnActivity extends AppCompatActivity {

    /** 칩 순서와 같은 순서의 유형 (null = 전체) */
    private static final String[] CHIP_TYPES = {
            null, MyPageStore.CLAIM_CANCEL, MyPageStore.CLAIM_RETURN, MyPageStore.CLAIM_EXCHANGE
    };

    private static final int COLOR_PROCESSING = Color.parseColor("#E53935");
    private static final int COLOR_FINISHED = Color.parseColor("#5A5A5A");

    private TextView[] chips;
    private TextView tvClaimCount, tvEmpty;
    private LinearLayout containerClaims;

    private int currentChip = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cancel_return);

        MyPageUi.setupToolbar(this, "취소/반품/교환 내역");

        chips = new TextView[]{
                findViewById(R.id.chipAll),
                findViewById(R.id.chipCancel),
                findViewById(R.id.chipReturn),
                findViewById(R.id.chipExchange)
        };
        tvClaimCount = findViewById(R.id.tvClaimCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        containerClaims = findViewById(R.id.containerClaims);

        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            chips[i].setOnClickListener(v -> {
                currentChip = index;
                renderClaims();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderClaims();
    }

    private void renderClaims() {
        MyPageUi.selectChip(chips, currentChip);

        List<Claim> claims = MyPageStore.getClaims(CHIP_TYPES[currentChip]);
        tvClaimCount.setText(claims.size() + "건");
        tvEmpty.setVisibility(claims.isEmpty() ? View.VISIBLE : View.GONE);

        containerClaims.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Claim claim : claims) {
            containerClaims.addView(createClaimView(inflater, claim));
        }
    }

    private View createClaimView(LayoutInflater inflater, Claim claim) {
        View view = inflater.inflate(R.layout.item_claim, containerClaims, false);

        ((TextView) view.findViewById(R.id.tvClaimType)).setText(claim.type);
        ((TextView) view.findViewById(R.id.tvRequestDate)).setText(claim.requestDate + " 신청");
        ((TextView) view.findViewById(R.id.tvProductName)).setText(claim.productName);
        ((TextView) view.findViewById(R.id.tvOption)).setText(claim.option);
        ((TextView) view.findViewById(R.id.tvPrice)).setText(MyPageStore.formatPrice(claim.price));
        ((TextView) view.findViewById(R.id.tvReason)).setText("사유: " + claim.reason);
        ((TextView) view.findViewById(R.id.tvOrderNo)).setText("주문번호 " + claim.orderNo);

        boolean processing = MyPageStore.CLAIM_STATE_PROCESSING.equals(claim.state);
        TextView tvState = view.findViewById(R.id.tvClaimState);
        tvState.setText(claim.state);
        tvState.setTextColor(processing ? COLOR_PROCESSING : COLOR_FINISHED);

        // 처리중인 반품/교환만 철회 가능 (취소는 바로 처리완료)
        Button btnWithdraw = view.findViewById(R.id.btnWithdraw);
        btnWithdraw.setVisibility(processing ? View.VISIBLE : View.GONE);
        btnWithdraw.setOnClickListener(v -> MyPageUi.confirm(this, "신청 철회",
                claim.type + " 신청을 철회하시겠습니까?", () -> {
                    MyPageStore.withdrawClaim(claim);
                    Toast.makeText(this, "신청이 철회되었습니다.", Toast.LENGTH_SHORT).show();
                    renderClaims();
                }));

        // 철회된 내역은 흐리게
        view.setAlpha(MyPageStore.CLAIM_STATE_WITHDRAWN.equals(claim.state) ? 0.5f : 1f);
        return view;
    }
}
