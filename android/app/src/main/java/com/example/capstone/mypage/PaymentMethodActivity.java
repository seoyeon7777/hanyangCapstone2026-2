package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.PaymentCard;
import com.example.capstone.ui.AppDialog;
import com.example.capstone.ui.KoreanLineBreak;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_method);

        MyPageUi.setupToolbar(this, "결제 수단 관리");

        tvCardCount = findViewById(R.id.tvCardCount);
        tvEmptyCard = findViewById(R.id.tvEmptyCard);
        containerCards = findViewById(R.id.containerCards);

        findViewById(R.id.btnAddCard).setOnClickListener(v -> {
            if (MyPageStore.getCards().size() >= MAX_CARD) {
                toast("카드는 최대 " + MAX_CARD + "장까지 등록할 수 있습니다.");
                return;
            }
            showAddCardSheet();
        });

        KoreanLineBreak.apply(findViewById(R.id.tvPayNotice));

        renderCards();
        renderEasyPay();
    }

    private void renderCards() {
        List<PaymentCard> cards = MyPageStore.getCards();
        tvCardCount.setText(cards.size() + " / " + MAX_CARD);
        tvEmptyCard.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);

        containerCards.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PaymentCard card : cards) {
            View view = inflater.inflate(R.layout.item_payment_card, containerCards, false);

            ((TextView) view.findViewById(R.id.tvCompany)).setText(card.company);
            ((TextView) view.findViewById(R.id.tvCardNumber)).setText(card.getMaskedNumber());
            ((TextView) view.findViewById(R.id.tvExpiry)).setText("VALID THRU " + card.expiry);
            view.findViewById(R.id.tvDefaultBadge)
                    .setVisibility(card.isDefault ? View.VISIBLE : View.GONE);

            TextView btnSetDefault = view.findViewById(R.id.btnSetDefault);
            btnSetDefault.setVisibility(card.isDefault ? View.GONE : View.VISIBLE);
            btnSetDefault.setOnClickListener(v -> {
                MyPageStore.setDefaultCard(card);
                toast(card.company + " 카드를 기본 결제 수단으로 설정했어요.");
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

    private void showAddCardSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_card, null, false);
        sheet.setContentView(view);

        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        if (sheet.getWindow() != null) {
            sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        TextView tvPreviewCompany = view.findViewById(R.id.tvPreviewCompany);
        TextView tvPreviewNumber = view.findViewById(R.id.tvPreviewNumber);
        TextView tvPreviewExpiry = view.findViewById(R.id.tvPreviewExpiry);
        EditText etCardNumber = view.findViewById(R.id.etCardNumber);
        EditText etExpiry = view.findViewById(R.id.etExpiry);
        EditText etCardPw = view.findViewById(R.id.etCardPw);
        TextView btnRegister = view.findViewById(R.id.btnRegisterCard);

        String[] company = {null};

        Runnable refresh = () -> {
            String digits = etCardNumber.getText().toString().replaceAll("[^0-9]", "");
            boolean ready = company[0] != null
                    && digits.length() >= 15
                    && normalizeExpiry(etExpiry.getText().toString()) != null
                    && etCardPw.length() == 2;
            btnRegister.setAlpha(ready ? 1f : 0.35f);

            tvPreviewNumber.setText(previewNumber(digits));
            String expiry = etExpiry.getText().toString();
            tvPreviewExpiry.setText("VALID THRU " + (expiry.isEmpty() ? "MM/YY" : expiry));
        };

        MyPageUi.fillChoiceGrid(view.findViewById(R.id.layoutCompanies), CARD_COMPANIES, 3,
                null, value -> {
                    company[0] = value;
                    tvPreviewCompany.setText(value);
                    refresh.run();
                });

        etCardNumber.addTextChangedListener(new FormatWatcher(etCardNumber, refresh) {
            @Override
            String format(String digits) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < digits.length() && i < 16; i++) {
                    if (i > 0 && i % 4 == 0) {
                        sb.append(' ');
                    }
                    sb.append(digits.charAt(i));
                }
                return sb.toString();
            }
        });

        etExpiry.addTextChangedListener(new FormatWatcher(etExpiry, refresh) {
            @Override
            String format(String digits) {
                if (digits.length() > 4) {
                    digits = digits.substring(0, 4);
                }
                return digits.length() <= 2 ? digits : digits.substring(0, 2) + "/" + digits.substring(2);
            }
        });

        etCardPw.addTextChangedListener(new FormatWatcher(etCardPw, refresh) {
            @Override
            String format(String digits) {
                return digits;
            }
        });

        view.findViewById(R.id.btnCloseSheet).setOnClickListener(v -> sheet.dismiss());

        btnRegister.setOnClickListener(v -> {
            String number = etCardNumber.getText().toString().replaceAll("[^0-9]", "");
            String expiry = normalizeExpiry(etExpiry.getText().toString());

            if (company[0] == null) {
                toast("카드사를 선택해주세요.");
                return;
            }
            if (number.length() < 15) {
                toast("카드번호를 정확히 입력해주세요.");
                return;
            }
            if (expiry == null) {
                toast("유효기간을 정확히 입력해주세요. (지난 날짜는 안 돼요)");
                return;
            }
            if (etCardPw.length() != 2) {
                toast("카드 비밀번호 앞 2자리를 입력해주세요.");
                return;
            }

            // [여기에 DB 입력] PG사 카드 등록(빌링키 발급) 후 끝 4자리만 저장 (payments)
            MyPageStore.addCard(company[0], number, expiry);
            toast("카드가 등록되었습니다.");
            renderCards();
            sheet.dismiss();
        });

        refresh.run();
        sheet.show();
    }

    private static String previewNumber(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(i < digits.length() ? digits.charAt(i) : '•');
        }
        return sb.toString();
    }

    private abstract static class FormatWatcher implements TextWatcher {
        private final EditText editText;
        private final Runnable onChanged;
        private boolean editing;

        FormatWatcher(EditText editText, Runnable onChanged) {
            this.editText = editText;
            this.onChanged = onChanged;
        }

        abstract String format(String digits);

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (editing) {
                return;
            }
            String formatted = format(s.toString().replaceAll("[^0-9]", ""));
            if (!formatted.equals(s.toString())) {
                editing = true;
                editText.setText(formatted);
                editText.setSelection(formatted.length());
                editing = false;
            }
            onChanged.run();
        }
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

    private void renderEasyPay() {
        bindEasyPay(findViewById(R.id.tvKakaoStatus), findViewById(R.id.btnKakaoPay),
                "카카오페이", MyPageStore.isKakaoPayLinked(), linked -> {
                    // [여기에 DB 입력] 카카오페이 정기결제 연동/해제 (payments)
                    MyPageStore.setKakaoPayLinked(linked);
                });
        bindEasyPay(findViewById(R.id.tvNaverStatus), findViewById(R.id.btnNaverPay),
                "네이버페이", MyPageStore.isNaverPayLinked(), linked -> {
                    // [여기에 DB 입력] 네이버페이 연동/해제 (payments)
                    MyPageStore.setNaverPayLinked(linked);
                });
    }

    private void bindEasyPay(TextView tvStatus, TextView btn, String name, boolean linked,
                             java.util.function.Consumer<Boolean> save) {
        tvStatus.setText(linked ? "연결됨" : "연결 안 됨");
        tvStatus.setTextColor(linked ? 0xFF111111 : 0xFF999999);
        btn.setText(linked ? "연결 해제" : "연결하기");

        btn.setOnClickListener(v -> {
            if (!linked) {
                save.accept(true);
                toast(name + "가 연결되었습니다.");
                renderEasyPay();
                return;
            }
            AppDialog.show(this, false, name + " 연결 해제",
                    name + " 연결을 해제하면 주문할 때 " + name + "로 결제할 수 없어요.",
                    "취소", null,
                    "연결 해제", () -> {
                        save.accept(false);
                        toast(name + " 연결이 해제되었습니다.");
                        renderEasyPay();
                    });
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
