package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.PaymentCard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Calendar;
import java.util.List;

/** 마이페이지 > 결제 수단 관리 (payments) */
public class PaymentMethodActivity extends AppCompatActivity {

    private static final int MAX_CARD = 5;

    private static final String[] CARD_COMPANIES = {
            "신한카드", "삼성카드", "현대카드", "KB국민카드", "롯데카드", "하나카드", "우리카드", "BC카드", "NH농협카드"
    };

    private TextView tvCardCount, tvEmptyCard;
    private LinearLayout containerCards;
    private Button btnAddCard;
    private SwitchCompat switchKakaoPay, switchNaverPay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_method);

        MyPageUi.setupToolbar(this, "결제 수단 관리");

        tvCardCount = findViewById(R.id.tvCardCount);
        tvEmptyCard = findViewById(R.id.tvEmptyCard);
        containerCards = findViewById(R.id.containerCards);
        btnAddCard = findViewById(R.id.btnAddCard);
        switchKakaoPay = findViewById(R.id.switchKakaoPay);
        switchNaverPay = findViewById(R.id.switchNaverPay);

        switchKakaoPay.setChecked(MyPageStore.isKakaoPayLinked());
        switchNaverPay.setChecked(MyPageStore.isNaverPayLinked());

        btnAddCard.setOnClickListener(v -> {
            if (MyPageStore.getCards().size() >= MAX_CARD) {
                Toast.makeText(this, "카드는 최대 " + MAX_CARD + "장까지 등록할 수 있습니다.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showAddCardDialog();
        });

        switchKakaoPay.setOnCheckedChangeListener((b, checked) -> {
            MyPageStore.setKakaoPayLinked(checked);
            Toast.makeText(this, checked ? "카카오페이가 연결되었습니다." : "카카오페이 연결이 해제되었습니다.",
                    Toast.LENGTH_SHORT).show();
        });

        switchNaverPay.setOnCheckedChangeListener((b, checked) -> {
            MyPageStore.setNaverPayLinked(checked);
            Toast.makeText(this, checked ? "네이버페이가 연결되었습니다." : "네이버페이 연결이 해제되었습니다.",
                    Toast.LENGTH_SHORT).show();
        });

        renderCards();
    }

    private void renderCards() {
        List<PaymentCard> cards = MyPageStore.getCards();
        tvCardCount.setText("등록 카드 " + cards.size() + "개");
        tvEmptyCard.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);

        containerCards.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PaymentCard card : cards) {
            View view = inflater.inflate(R.layout.item_payment_card, containerCards, false);

            ((TextView) view.findViewById(R.id.tvCompany)).setText(card.company);
            ((TextView) view.findViewById(R.id.tvCardNumber)).setText(card.getMaskedNumber());
            ((TextView) view.findViewById(R.id.tvExpiry)).setText("유효기간 " + card.expiry);
            view.findViewById(R.id.tvDefaultBadge)
                    .setVisibility(card.isDefault ? View.VISIBLE : View.GONE);

            Button btnSetDefault = view.findViewById(R.id.btnSetDefault);
            btnSetDefault.setVisibility(card.isDefault ? View.GONE : View.VISIBLE);
            btnSetDefault.setOnClickListener(v -> {
                MyPageStore.setDefaultCard(card);
                renderCards();
            });

            view.findViewById(R.id.btnDelete).setOnClickListener(v ->
                    MyPageUi.confirm(this, "카드 삭제",
                            card.company + " (" + card.lastFour + ") 카드를 삭제하시겠습니까?", () -> {
                                MyPageStore.deleteCard(card);
                                renderCards();
                            }));

            containerCards.addView(view);
        }
    }

    private void showAddCardDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_card, null);
        Spinner spCompany = dialogView.findViewById(R.id.spCompany);
        EditText etCardNumber = dialogView.findViewById(R.id.etCardNumber);
        EditText etExpiry = dialogView.findViewById(R.id.etExpiry);
        EditText etCardPw = dialogView.findViewById(R.id.etCardPw);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CARD_COMPANIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCompany.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("카드 등록")
                .setView(dialogView)
                .setPositiveButton("등록", null)
                .setNegativeButton("취소", null)
                .create();

        // 입력이 틀렸을 때 다이얼로그가 닫히지 않도록 버튼 리스너를 show() 뒤에 직접 단다.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String number = etCardNumber.getText().toString().replaceAll("[^0-9]", "");
            String expiry = normalizeExpiry(etExpiry.getText().toString());

            if (number.length() < 15) {
                Toast.makeText(this, "카드번호를 정확히 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (expiry == null) {
                Toast.makeText(this, "유효기간을 MM/YY 형식으로 정확히 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (etCardPw.getText().length() != 2) {
                Toast.makeText(this, "카드 비밀번호 앞 2자리를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            MyPageStore.addCard(spCompany.getSelectedItem().toString(), number, expiry);
            Toast.makeText(this, "카드가 등록되었습니다.", Toast.LENGTH_SHORT).show();
            renderCards();
            dialog.dismiss();
        }));

        dialog.show();
    }

    /** "1228" 또는 "12/28" 을 "12/28"로 맞춘다. 형식이 틀렸거나 이미 지난 날짜면 null */
    private String normalizeExpiry(String input) {
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() != 4) {
            return null;
        }

        int month = Integer.parseInt(digits.substring(0, 2));
        int year = 2000 + Integer.parseInt(digits.substring(2));
        if (month < 1 || month > 12) {
            return null;
        }

        Calendar now = Calendar.getInstance();
        int nowYear = now.get(Calendar.YEAR);
        int nowMonth = now.get(Calendar.MONTH) + 1;
        if (year < nowYear || (year == nowYear && month < nowMonth)) {
            return null;
        }

        return digits.substring(0, 2) + "/" + digits.substring(2);
    }
}
