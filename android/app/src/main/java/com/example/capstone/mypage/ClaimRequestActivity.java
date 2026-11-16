package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Order;
import com.example.capstone.data.MyPageStore.RefundAccount;
import com.example.capstone.data.RecommendApi;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class ClaimRequestActivity extends AppCompatActivity {

    private static final String EXTRA_ORDER_NO = "claim_order_no";
    private static final String EXTRA_CANCEL = "claim_cancel";

    private Order order;
    private boolean isCancel;
    private boolean exchange = false;

    private EditText etDetail;
    private TextView btnTypeReturn, btnTypeExchange;
    private LinearLayout containerAccounts;

    private String selectedAccountNo;

    public static Intent newIntent(Context context, String orderNo, boolean cancel) {
        Intent intent = new Intent(context, ClaimRequestActivity.class);
        intent.putExtra(EXTRA_ORDER_NO, orderNo);
        intent.putExtra(EXTRA_CANCEL, cancel);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claim_request);

        order = MyPageStore.findOrder(getIntent().getStringExtra(EXTRA_ORDER_NO));
        isCancel = getIntent().getBooleanExtra(EXTRA_CANCEL, true);
        if (order == null) {
            finish();
            return;
        }

        MyPageUi.setupToolbar(this, isCancel ? "주문 취소" : "반품·교환 신청");

        TextView brand = findViewById(R.id.tvClaimBrand);
        brand.setText(order.brand);
        brand.setVisibility(TextUtils.isEmpty(order.brand) ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.tvClaimName)).setText(order.productName);
        ((TextView) findViewById(R.id.tvClaimOption)).setText(order.option.contains("총 ")
                ? order.option
                : (order.option.isEmpty() ? "" : order.option + " / ") + order.quantity + "개");
        ((TextView) findViewById(R.id.tvClaimPrice)).setText(MyPageStore.formatPrice(order.getTotalPrice()));

        etDetail = findViewById(R.id.etReasonDetail);
        btnTypeReturn = findViewById(R.id.btnTypeReturn);
        btnTypeExchange = findViewById(R.id.btnTypeExchange);
        containerAccounts = findViewById(R.id.containerRefundAccounts);

        findViewById(R.id.layoutClaimType).setVisibility(isCancel ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.tvReasonTitle)).setText(isCancel ? "취소 사유" : "사유");
        btnTypeReturn.setOnClickListener(v -> setExchange(false));
        btnTypeExchange.setOnClickListener(v -> setExchange(true));

        ((TextView) findViewById(R.id.tvRefundAmount)).setText(MyPageStore.formatPrice(order.getTotalPrice()));
        findViewById(R.id.btnAddRefundAccount).setOnClickListener(v ->
                startActivity(new Intent(this, RefundAccountActivity.class)));

        findViewById(R.id.btnClaimSubmit).setOnClickListener(v -> submit());
        setExchange(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (order != null) {
            renderRefund();
        }
    }

    private void setExchange(boolean value) {
        exchange = value;
        styleType(btnTypeReturn, !value);
        styleType(btnTypeExchange, value);
        findViewById(R.id.layoutRefund).setVisibility(value ? View.GONE : View.VISIBLE);
    }

    private void styleType(TextView button, boolean selected) {
        button.setBackgroundResource(selected ? R.drawable.bg_card_active : R.drawable.bg_card);
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void renderRefund() {
        TextView method = findViewById(R.id.tvRefundMethod);
        View accountsLayout = findViewById(R.id.layoutRefundAccounts);

        if (!order.paidByCard() && order.status == MyPageStore.STATUS_PENDING) {
            method.setText("입금 전 주문이라 환불할 금액이 없어요");
            method.setVisibility(View.VISIBLE);
            accountsLayout.setVisibility(View.GONE);
            return;
        }

        accountsLayout.setVisibility(View.VISIBLE);
        List<RefundAccount> accounts = MyPageStore.getRefundAccounts();

        if (selectedAccountNo != null && findAccount(selectedAccountNo) == null) {
            selectedAccountNo = null;
        }
        if (!order.paidByCard() && selectedAccountNo == null) {
            RefundAccount preferred = MyPageStore.getRefundAccount();
            if (preferred == null && !accounts.isEmpty()) {
                preferred = accounts.get(0);
            }
            selectedAccountNo = preferred == null ? null : preferred.accountNo;
        }

        method.setVisibility(View.VISIBLE);
        if (order.paidByCard()) {
            method.setText("고른 계좌로 환불돼요");
            method.setVisibility(selectedAccountNo == null ? View.GONE : View.VISIBLE);
        } else {
            method.setText(accounts.isEmpty()
                    ? "환불받을 계좌를 등록해 주세요"
                    : "환불받을 계좌를 골라 주세요");
        }

        containerAccounts.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (RefundAccount account : accounts) {
            TextView card = new TextView(this);
            boolean selected = account.accountNo.equals(selectedAccountNo);
            card.setText(account.bank + "  " + account.getMaskedAccountNo()
                    + "\n예금주 " + account.holder + (account.isDefault ? " · 대표 계좌" : ""));
            card.setTextSize(14);
            card.setTextColor(0xFF111111);
            card.setLineSpacing(0, 1.3f);
            card.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            int pad = Math.round(16 * density);
            card.setPadding(pad, pad, pad, pad);
            card.setBackgroundResource(selected ? R.drawable.bg_card_active : R.drawable.bg_card);
            card.setOnClickListener(v -> {
                boolean again = account.accountNo.equals(selectedAccountNo);
                selectedAccountNo = again && order.paidByCard() ? null : account.accountNo;
                renderRefund();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(10 * density);
            containerAccounts.addView(card, lp);
        }
    }

    private String cardName() {
        return order.cardCompany.isEmpty() ? "카드" : order.cardCompany + "카드";
    }

    private RefundAccount findAccount(String accountNo) {
        for (RefundAccount account : MyPageStore.getRefundAccounts()) {
            if (account.accountNo.equals(accountNo)) {
                return account;
            }
        }
        return null;
    }

    private void submit() {
        String reason = etDetail.getText().toString().trim();
        if (reason.isEmpty()) {
            Toast.makeText(this, "사유를 적어 주세요.", Toast.LENGTH_SHORT).show();
            etDetail.requestFocus();
            return;
        }

        String type = isCancel ? MyPageStore.CLAIM_CANCEL
                : (exchange ? MyPageStore.CLAIM_EXCHANGE : MyPageStore.CLAIM_RETURN);

        String refund = "";
        if (!MyPageStore.CLAIM_EXCHANGE.equals(type)) {
            if (order.paidByCard() && selectedAccountNo == null) {
                refund = cardName() + " 결제 취소";
            } else if (order.paidByCard() || MyPageStore.needsRefundAccount(order)) {
                RefundAccount account = selectedAccountNo == null ? null : findAccount(selectedAccountNo);
                if (account == null) {
                    Toast.makeText(this, "환불받을 계좌를 등록하거나 골라 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                refund = account.bank + " " + account.getMaskedAccountNo();
            }
        }


        MyPageStore.requestClaim(order, type, reason, refund);
        if (MyPageStore.CLAIM_RETURN.equals(type)) {
            RecommendApi.recordReturn(this, order.orderNo);
        }
        Toast.makeText(this, type + " 신청이 완료되었습니다.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
