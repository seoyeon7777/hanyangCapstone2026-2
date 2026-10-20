package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.RefundAccount;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** 마이페이지 > 환불 계좌 관리 (refund_accounts) */
public class RefundAccountActivity extends AppCompatActivity {

    private static final String BANK_PLACEHOLDER = "은행을 선택해주세요";
    private static final String[] BANKS = {
            BANK_PLACEHOLDER, "KB국민은행", "신한은행", "우리은행", "하나은행", "NH농협은행",
            "IBK기업은행", "카카오뱅크", "토스뱅크", "케이뱅크", "SC제일은행", "우체국"
    };

    private LinearLayout containerRegistered, containerForm;
    private TextView tvBank, tvAccountNo, tvHolder;
    private Button btnChange, btnDelete, btnSave;
    private Spinner spBank;
    private EditText etAccountNo, etHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_account);

        MyPageUi.setupToolbar(this, "환불 계좌 관리");
        initViews();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, BANKS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBank.setAdapter(adapter);

        btnChange.setOnClickListener(v -> showForm(MyPageStore.getRefundAccount()));
        btnDelete.setOnClickListener(v -> MyPageUi.confirm(this, "환불 계좌 삭제",
                "등록된 환불 계좌를 삭제하시겠습니까?", () -> {
                    MyPageStore.deleteRefundAccount();
                    render();
                }));
        btnSave.setOnClickListener(v -> saveAccount());

        render();
    }

    private void initViews() {
        containerRegistered = findViewById(R.id.containerRegistered);
        containerForm = findViewById(R.id.containerForm);
        tvBank = findViewById(R.id.tvBank);
        tvAccountNo = findViewById(R.id.tvAccountNo);
        tvHolder = findViewById(R.id.tvHolder);
        btnChange = findViewById(R.id.btnChange);
        btnDelete = findViewById(R.id.btnDelete);
        btnSave = findViewById(R.id.btnSave);
        spBank = findViewById(R.id.spBank);
        etAccountNo = findViewById(R.id.etAccountNo);
        etHolder = findViewById(R.id.etHolder);
    }

    /** 등록된 계좌가 있으면 계좌 카드를, 없으면 입력 폼을 보여준다. */
    private void render() {
        RefundAccount account = MyPageStore.getRefundAccount();

        if (account == null) {
            containerRegistered.setVisibility(View.GONE);
            showForm(null);
            return;
        }

        containerRegistered.setVisibility(View.VISIBLE);
        containerForm.setVisibility(View.GONE);
        tvBank.setText(account.bank);
        tvAccountNo.setText(account.getMaskedAccountNo());
        tvHolder.setText("예금주 " + account.holder);
    }

    /** 입력 폼을 연다. 변경일 때는 기존 값을 채워둔다. */
    private void showForm(RefundAccount origin) {
        containerForm.setVisibility(View.VISIBLE);

        if (origin == null) {
            spBank.setSelection(0);
            etAccountNo.setText("");
            etHolder.setText(MyPageStore.getUser().name);
            btnSave.setText("계좌 등록");
        } else {
            for (int i = 0; i < BANKS.length; i++) {
                if (BANKS[i].equals(origin.bank)) spBank.setSelection(i);
            }
            etAccountNo.setText(origin.accountNo);
            etHolder.setText(origin.holder);
            btnSave.setText("계좌 변경");
        }
    }

    private void saveAccount() {
        String bank = spBank.getSelectedItem().toString();
        String accountNo = etAccountNo.getText().toString().replaceAll("[^0-9]", "");
        String holder = etHolder.getText().toString().trim();

        if (BANK_PLACEHOLDER.equals(bank)) {
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
        if (!holder.equals(MyPageStore.getUser().name)) {
            toast("회원 본인 명의의 계좌만 등록할 수 있습니다.");
            return;
        }

        // [여기에 DB 입력] 예금주 실명 확인 후 환불 계좌 저장 (refund_accounts)
        MyPageStore.saveRefundAccount(new RefundAccount(bank, accountNo, holder));
        toast("환불 계좌가 저장되었습니다.");
        render();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
