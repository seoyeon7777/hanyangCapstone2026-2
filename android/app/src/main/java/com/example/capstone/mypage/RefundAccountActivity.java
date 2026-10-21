package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.RefundAccount;
import com.example.capstone.ui.KoreanLineBreak;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
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

import java.util.List;

/** 마이페이지 > 환불 계좌 관리 (refund_accounts) */
public class RefundAccountActivity extends AppCompatActivity {

    private static final int MAX_ACCOUNT = 5;

    private static final String[] BANKS = {
            "KB국민은행", "신한은행", "우리은행", "하나은행", "NH농협은행", "IBK기업은행",
            "카카오뱅크", "토스뱅크", "케이뱅크", "SC제일은행", "우체국", "새마을금고"
    };

    private TextView tvAccountCount, tvEmptyAccount;
    private LinearLayout containerAccounts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_account);

        MyPageUi.setupToolbar(this, "환불 계좌 관리");

        tvAccountCount = findViewById(R.id.tvAccountCount);
        tvEmptyAccount = findViewById(R.id.tvEmptyAccount);
        containerAccounts = findViewById(R.id.containerAccounts);

        findViewById(R.id.btnAddAccount).setOnClickListener(v -> {
            if (MyPageStore.getRefundAccounts().size() >= MAX_ACCOUNT) {
                toast("환불 계좌는 최대 " + MAX_ACCOUNT + "개까지 등록할 수 있습니다.");
                return;
            }
            showAddAccountSheet();
        });

        KoreanLineBreak.apply(findViewById(R.id.tvRefundNotice));

        renderAccounts();
    }

    private void renderAccounts() {
        List<RefundAccount> accounts = MyPageStore.getRefundAccounts();
        tvAccountCount.setText(accounts.size() + " / " + MAX_ACCOUNT);
        tvEmptyAccount.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);

        containerAccounts.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (RefundAccount account : accounts) {
            View view = inflater.inflate(R.layout.item_refund_account, containerAccounts, false);

            ((TextView) view.findViewById(R.id.tvBank)).setText(account.bank);
            ((TextView) view.findViewById(R.id.tvAccountNo)).setText(account.getMaskedAccountNo());
            ((TextView) view.findViewById(R.id.tvHolder)).setText("예금주 " + account.holder);
            view.findViewById(R.id.tvDefaultBadge)
                    .setVisibility(account.isDefault ? View.VISIBLE : View.GONE);
            view.findViewById(R.id.layoutAccountCard).setBackgroundResource(
                    account.isDefault ? R.drawable.bg_card_active : R.drawable.bg_card);

            TextView btnSetDefault = view.findViewById(R.id.btnSetDefault);
            btnSetDefault.setVisibility(account.isDefault ? View.GONE : View.VISIBLE);
            btnSetDefault.setOnClickListener(v -> {
                // [여기에 DB 입력] 대표 환불 계좌 변경 (refund_accounts)
                MyPageStore.setDefaultRefundAccount(account);
                toast(account.bank + " 계좌를 대표 계좌로 설정했어요.");
                renderAccounts();
            });

            view.findViewById(R.id.btnDelete).setOnClickListener(v ->
                    MyPageUi.confirm(this, "환불 계좌 삭제",
                            account.bank + " " + account.getMaskedAccountNo() + " 계좌를 삭제하시겠습니까?", () -> {
                                // [여기에 DB 입력] 환불 계좌 삭제 (refund_accounts)
                                MyPageStore.deleteRefundAccount(account);
                                renderAccounts();
                            }));

            containerAccounts.addView(view);
        }
    }

    private void showAddAccountSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_refund_account, null, false);
        sheet.setContentView(view);

        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        if (sheet.getWindow() != null) {
            sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        EditText etAccountNo = view.findViewById(R.id.etAccountNo);
        EditText etHolder = view.findViewById(R.id.etHolder);
        TextView tvHolderNote = view.findViewById(R.id.tvHolderNote);
        TextView btnRegister = view.findViewById(R.id.btnRegisterAccount);

        String[] bank = {null};

        String memberName = MyPageStore.getUser().name;
        boolean hasName = memberName != null && !memberName.trim().isEmpty();
        if (hasName) {
            etHolder.setText(memberName);
            etHolder.setEnabled(false);
            etHolder.setTextColor(0xFF777777);
            tvHolderNote.setText("예금주는 회원님 이름으로 자동 입력돼요. 본인 명의 계좌만 등록할 수 있어요.");
        }

        Runnable refresh = () -> {
            int length = etAccountNo.getText().toString().replaceAll("[^0-9]", "").length();
            boolean ready = bank[0] != null
                    && length >= 10 && length <= 16
                    && etHolder.getText().toString().trim().length() > 0;
            btnRegister.setAlpha(ready ? 1f : 0.35f);
        };

        MyPageUi.fillChoiceGrid(view.findViewById(R.id.layoutBanks), BANKS, 3, null, value -> {
            bank[0] = value;
            refresh.run();
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        };
        etAccountNo.addTextChangedListener(watcher);
        etHolder.addTextChangedListener(watcher);

        view.findViewById(R.id.btnCloseSheet).setOnClickListener(v -> sheet.dismiss());

        btnRegister.setOnClickListener(v -> {
            String accountNo = etAccountNo.getText().toString().replaceAll("[^0-9]", "");
            String holder = etHolder.getText().toString().trim();

            if (bank[0] == null) {
                toast("은행을 선택해주세요.");
                return;
            }
            if (accountNo.length() < 10 || accountNo.length() > 16) {
                toast("계좌번호를 정확히 입력해주세요. (10~16자리)");
                return;
            }
            if (TextUtils.isEmpty(holder)) {
                toast("예금주명을 입력해주세요.");
                return;
            }
            if (hasName && !holder.equals(memberName)) {
                toast("회원 본인 명의의 계좌만 등록할 수 있습니다.");
                return;
            }
            if (MyPageStore.hasRefundAccount(bank[0], accountNo)) {
                toast("이미 등록된 계좌예요.");
                return;
            }

            // [여기에 DB 입력] 예금주 실명 확인(1원 인증 등) 후 환불 계좌 저장 (refund_accounts)
            MyPageStore.addRefundAccount(new RefundAccount(bank[0], accountNo, holder));
            toast("환불 계좌가 등록되었습니다.");
            renderAccounts();
            sheet.dismiss();
        });

        refresh.run();
        sheet.show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
