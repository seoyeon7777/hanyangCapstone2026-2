package com.example.capstone.login;

import com.example.capstone.R;
import com.example.capstone.data.LocalAccounts;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * 계정(아이디) 찾기.
 * 휴대전화 번호 또는 이메일로 가입한 계정이 있으면 인증번호를 보내고(안내 창),
 * 인증번호가 맞으면 아이디를 알려 준다. 계정이 없으면 없다고 알려 준다.
 */
public class FindIdActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;

    private Button btnByPhone, btnByEmail;
    private TextView tvContactLabel;
    private EditText etPhone, etAuthCode;
    private Button btnRequestAuth, btnVerifyCode, btnConfirm;

    private LinearLayout containerResult;
    private TextView tvFoundAccount;

    private final AuthCode authCode = new AuthCode();

    private boolean byPhone = true;
    private boolean isCodeVerified = false;
    private boolean isAccountFound = false;

    /** 인증번호를 보낸 연락처(정규화한 값)와 그 연락처로 찾은 계정 */
    private String sentTarget;
    private LocalAccounts.Account foundAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_id);

        initViews();
        applyMethod();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        btnByPhone = findViewById(R.id.btnByPhone);
        btnByEmail = findViewById(R.id.btnByEmail);

        tvContactLabel = findViewById(R.id.tvContactLabel);
        etPhone = findViewById(R.id.etPhone);
        etAuthCode = findViewById(R.id.etAuthCode);

        btnRequestAuth = findViewById(R.id.btnRequestAuth);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnConfirm = findViewById(R.id.btnConfirm);

        containerResult = findViewById(R.id.containerResult);
        tvFoundAccount = findViewById(R.id.tvFoundAccount);

        authCode.bindTimer(findViewById(R.id.tvTimer));
    }

    @Override
    protected void onDestroy() {
        authCode.clear();       // 남은 시간 갱신을 멈춘다
        super.onDestroy();
    }

    /** 찾는 방법(휴대전화/이메일)에 맞게 입력칸과 버튼 모양을 바꾼다 */
    private void applyMethod() {
        styleToggle(btnByPhone, byPhone);
        styleToggle(btnByEmail, !byPhone);

        if (byPhone) {
            tvContactLabel.setText("휴대전화 번호");
            etPhone.setHint("010-0000-0000");
            PhoneInput.attach(etPhone);
        } else {
            tvContactLabel.setText("이메일");
            etPhone.setHint("example@gmail.com");
            PhoneInput.detach(etPhone);
            etPhone.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
    }

    private void styleToggle(Button button, boolean selected) {
        button.setBackgroundResource(selected ? R.drawable.bg_button : R.drawable.bg_button_outlin);
        button.setTextColor(selected ? Color.WHITE : Color.BLACK);
    }

    /** 처음 상태로 되돌린다 (연락처를 고치거나 찾는 방법을 바꿨을 때) */
    private void resetFlow() {
        authCode.clear();
        sentTarget = null;
        foundAccount = null;
        isCodeVerified = false;
        isAccountFound = false;
        etAuthCode.setText("");
        btnVerifyCode.setText("인증확인");
        btnVerifyCode.setEnabled(true);
        btnVerifyCode.setVisibility(View.GONE);
        containerResult.setVisibility(View.GONE);
        btnConfirm.setText("아이디 찾기");
        setConfirmEnabled(false);
        btnRequestAuth.setText("인증받기");
    }

    /** 인증확인을 마쳐야 넘어갈 수 있다. 마치기 전에는 버튼을 눌러도 반응하지 않고 흐리게 보인다 */
    private void setConfirmEnabled(boolean enabled) {
        btnConfirm.setEnabled(enabled);
        btnConfirm.setAlpha(enabled ? 1f : 0.4f);
    }

    private void setMethod(boolean phone) {
        if (byPhone == phone) {
            return;
        }
        byPhone = phone;
        etPhone.setText("");
        resetFlow();
        applyMethod();
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> LoginMainActivity.goHome(this));

        btnByPhone.setOnClickListener(v -> setMethod(true));
        btnByEmail.setOnClickListener(v -> setMethod(false));

        // 인증받기 버튼
        btnRequestAuth.setOnClickListener(v -> requestAuthCode());

        // 인증번호를 입력하면 인증확인 버튼을 보여준다.
        etAuthCode.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnVerifyCode.setVisibility(
                        TextUtils.isEmpty(s.toString().trim()) ? View.GONE : View.VISIBLE);

                // 인증 후 번호를 고치면 다시 인증받아야 한다.
                if (isCodeVerified) {
                    isCodeVerified = false;
                    isAccountFound = false;
                    btnVerifyCode.setText("인증확인");
                    btnVerifyCode.setEnabled(true);
                    containerResult.setVisibility(View.GONE);
                    btnConfirm.setText("아이디 찾기");
                    setConfirmEnabled(false);
                }
            }
        });

        // 인증확인 버튼
        btnVerifyCode.setOnClickListener(v -> verifyAuthCode());

        // 아이디 찾기 버튼
        btnConfirm.setOnClickListener(v -> findAccount());
        setConfirmEnabled(false);
    }

    /** 입력한 연락처로 가입한 계정이 있으면 인증번호를 보낸다. 없으면 없다고 알려 준다. */
    private void requestAuthCode() {
        String contact = normalizedContact();

        if (contact == null) {
            Toast.makeText(this, byPhone
                            ? "휴대전화 번호를 정확히 입력해주세요."
                            : "올바른 이메일 형식이 아닙니다. (예: example@gmail.com)",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 이 휴대전화·이메일로 가입된 회원 조회 (users)
        LocalAccounts.Account account = byPhone
                ? LocalAccounts.findAccountByPhone(this, contact)
                : LocalAccounts.findAccountByEmail(this, contact);

        if (account == null) {
            Toast.makeText(this, byPhone
                            ? "이 휴대전화 번호로 가입된 계정이 없습니다."
                            : "이 이메일로 가입된 계정이 없습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String shown = byPhone ? LocalAccounts.formatPhone(contact) : contact;
        etPhone.setText(shown);
        etPhone.setSelection(shown.length());
        resetFlow();

        // [여기에 DB 입력] 발송한 인증번호를 만료시각과 함께 저장 (auth_codes)
        foundAccount = account;
        sentTarget = contact;
        authCode.issue(this, contact, byPhone ? AuthCode.maskPhone(contact) : AuthCode.maskEmail(contact));
        btnRequestAuth.setText("재전송");      // 언제든 다시 받을 수 있고, 받으면 시간이 처음부터 시작된다
    }

    /** 입력한 인증번호를 확인한다. */
    private void verifyAuthCode() {
        String input = etAuthCode.getText().toString().trim();

        if (input.length() != AuthCode.LENGTH) {
            Toast.makeText(this, "인증번호 " + AuthCode.LENGTH + "자리를 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 칸의 번호가 아니라 마지막으로 인증받기를 눌러 인증번호가 나간 번호로 확인한다
        String error = authCode.check(input, sentTarget);

        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        isCodeVerified = true;
        btnVerifyCode.setText("인증완료");
        btnVerifyCode.setEnabled(false);
        setConfirmEnabled(true);
        Toast.makeText(this, "인증이 완료되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /** 본인확인을 마친 뒤 아이디를 화면에 보여준다. */
    private void findAccount() {

        // 이미 아이디를 찾은 뒤에는 로그인 화면으로 돌아간다.
        if (isAccountFound) {
            LoginMainActivity.backToLogin(this);
            return;
        }

        if (foundAccount == null) {
            Toast.makeText(this, "인증받기를 눌러 본인확인을 해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isCodeVerified) {
            Toast.makeText(this, "인증확인을 완료해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvFoundAccount.setText(foundAccount.id);
        containerResult.setVisibility(View.VISIBLE);

        isAccountFound = true;
        btnConfirm.setText("로그인하러 가기");
    }

    /** 입력한 연락처를 정규화한다 (전화번호는 010-0000-0000, 이메일은 소문자). 올바르지 않으면 null */
    private String normalizedContact() {
        String input = etPhone.getText().toString().replaceAll("\\s", "");

        if (byPhone) {
            return LocalAccounts.normalizePhone(input);
        }

        String email = input.toLowerCase(Locale.ROOT);
        if (email.indexOf('@') <= 0 || email.indexOf('@') != email.lastIndexOf('@')
                || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return null;
        }
        return email;
    }

    /** beforeTextChanged·afterTextChanged를 쓰지 않는 TextWatcher */
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}
    }
}
