package com.example.capstone.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.capstone.R;
import com.example.capstone.login.LoginSession;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 카드 결제 (시연용: 실제 결제는 하지 않는다).
 * ① 카드사 9개 + 더보기, 할부개월 선택 → "다음"
 * ② 결제 정보 확인 (결제카드·할부개월·이메일, 확인 체크) → "결제" → RESULT_OK로 닫고 결제 화면이 주문을 저장한다
 */
public class CardPaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "card_amount";

    /** 이 금액 이상이어야 할부(2~12개월)를 고를 수 있다 */
    static final int INSTALLMENT_MIN_AMOUNT = 50000;

    private static final String[] MAIN_CARDS = {
            "KB국민", "신한", "하나",
            "현대", "비씨", "농협(NH)",
            "우리", "카카오뱅크", "토스뱅크"
    };

    /** "+ 더보기"를 누르면 나오는 나머지 카드사 */
    private static final String[] MORE_CARDS = {
            "삼성", "롯데", "케이뱅크", "전북", "광주", "제주",
            "수협", "씨티", "우체국", "새마을금고", "신협"
    };

    private static final String MORE_LABEL = "+\n더보기";

    private View layoutSelect, layoutConfirm;
    private GridLayout gridCards;
    private Spinner spInstallment;
    private Button btnNext, btnPay;
    private TextView tvConfirmCard, tvConfirmInstallment;
    private CheckBox cbConfirm;

    private final List<TextView> cells = new ArrayList<>();
    private TextView moreCell;
    private String selectedCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_payment);

        int amount = getIntent().getIntExtra(EXTRA_AMOUNT, 0);
        String amountText = NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
        ((TextView) findViewById(R.id.tvCardAmount)).setText(amountText);

        layoutSelect = findViewById(R.id.layoutCardSelect);
        layoutConfirm = findViewById(R.id.layoutCardConfirm);
        gridCards = findViewById(R.id.gridCards);
        spInstallment = findViewById(R.id.spInstallment);
        btnNext = findViewById(R.id.btnCardNext);
        btnPay = findViewById(R.id.btnCardPay);
        tvConfirmCard = findViewById(R.id.tvConfirmCard);
        tvConfirmInstallment = findViewById(R.id.tvConfirmInstallment);
        cbConfirm = findViewById(R.id.cbConfirmPay);

        btnPay.setText(amountText + " 결제");

        buildCardGrid();
        setupInstallment(amount);

        String email = LoginSession.getEmail(this);
        if (email != null) {
            ((EditText) findViewById(R.id.etConfirmEmail)).setText(email);
        }

        findViewById(R.id.btnCardBack).setOnClickListener(v -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        btnNext.setOnClickListener(v -> showConfirm());
        findViewById(R.id.btnChangeCard).setOnClickListener(v -> showSelect());

        cbConfirm.setOnCheckedChangeListener((b, checked) -> setButtonEnabled(btnPay, checked));
        btnPay.setOnClickListener(v -> {
            if (!cbConfirm.isChecked()) {
                return;
            }
            setResult(RESULT_OK);
            finish();
        });

        setButtonEnabled(btnNext, false);
        setButtonEnabled(btnPay, false);
    }

    /** 확인 단계면 선택 단계로, 선택 단계면 결제 화면으로 (결제 안 함) */
    private void goBack() {
        if (layoutConfirm.getVisibility() == View.VISIBLE) {
            showSelect();
            return;
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void buildCardGrid() {
        float density = getResources().getDisplayMetrics().density;
        int gap = Math.round(5 * density);

        List<String> labels = new ArrayList<>();
        for (String card : MAIN_CARDS) {
            labels.add(card);
        }
        labels.add(MORE_LABEL);

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            TextView cell = new TextView(this);
            cell.setText(label);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(15);
            cell.setBackgroundResource(R.drawable.bg_pay_card_cell);
            cell.setTextColor(ContextCompat.getColorStateList(this, R.color.pay_card_text));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 3), GridLayout.spec(i % 3, 1f));
            params.width = 0;
            params.height = Math.round(64 * density);
            params.setMargins(gap, gap, gap, gap);
            cell.setLayoutParams(params);

            if (MORE_LABEL.equals(label)) {
                moreCell = cell;
                cell.setOnClickListener(v -> showMoreCards());
            } else {
                cell.setOnClickListener(v -> selectCard((TextView) v, label));
            }
            cells.add(cell);
            gridCards.addView(cell);
        }
    }

    private void showMoreCards() {
        new AlertDialog.Builder(this)
                .setTitle("카드사 선택")
                .setItems(MORE_CARDS, (dialog, which) -> {
                    String card = MORE_CARDS[which];
                    // 더보기 칸에 고른 카드사 이름을 보여 주고 선택 상태로
                    moreCell.setText(card);
                    selectCard(moreCell, card);
                })
                .show();
    }

    private void selectCard(TextView cell, String card) {
        for (TextView other : cells) {
            other.setSelected(false);
        }
        if (cell != moreCell) {
            moreCell.setText(MORE_LABEL);
        }
        cell.setSelected(true);
        selectedCard = card;
        setButtonEnabled(btnNext, true);
    }

    /** 할부는 5만원 이상만 (2~12개월). 5만원 미만은 일시불만 */
    private void setupInstallment(int amount) {
        List<String> months = new ArrayList<>();
        months.add("일시불");
        if (amount >= INSTALLMENT_MIN_AMOUNT) {
            for (int m = 2; m <= 12; m++) {
                months.add(m + "개월");
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, months);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spInstallment.setAdapter(adapter);
    }

    private void showConfirm() {
        if (selectedCard == null) {
            return;
        }
        tvConfirmCard.setText(selectedCard);
        tvConfirmInstallment.setText(String.valueOf(spInstallment.getSelectedItem()));
        cbConfirm.setChecked(false);
        layoutSelect.setVisibility(View.GONE);
        layoutConfirm.setVisibility(View.VISIBLE);
    }

    private void showSelect() {
        layoutConfirm.setVisibility(View.GONE);
        layoutSelect.setVisibility(View.VISIBLE);
    }

    /** 누를 수 있으면 검은색, 아니면 회색 (회원가입 본인확인 "다음"과 같음) */
    private static void setButtonEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setBackgroundResource(enabled ? R.drawable.bg_button : R.drawable.bg_button_gray);
    }
}
