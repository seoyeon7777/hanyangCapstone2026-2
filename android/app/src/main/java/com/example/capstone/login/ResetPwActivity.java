package com.example.capstone.login;

import com.example.capstone.R;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class ResetPwActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;

    private EditText etEmail, etAuthCode, etPassword, etPasswordConfirm;
    private Button btnRequestAuth, btnVerifyCode, btnConfirm;
    private ImageButton btnTogglePw1, btnTogglePw2;

    private LinearLayout containerNewPw;

    private boolean isCodeSent = false;
    private boolean isCodeVerified = false;

    private boolean isPwVisible = false;
    private boolean isPw2Visible = false;

    /** 인증번호 자릿수 */
    private static final int AUTH_CODE_LENGTH = 4;
    /** 비밀번호 최소 길이 */
    private static final int PASSWORD_MIN_LENGTH = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_pw);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etEmail = findViewById(R.id.etEmail);
        etAuthCode = findViewById(R.id.etAuthCode);
        etPassword = findViewById(R.id.etPassword);
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm);

        btnRequestAuth = findViewById(R.id.btnRequestAuth);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnConfirm = findViewById(R.id.btnConfirm);

        btnTogglePw1 = findViewById(R.id.btnTogglePw1);
        btnTogglePw2 = findViewById(R.id.btnTogglePw2);

        containerNewPw = findViewById(R.id.containerNewPw);
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> LoginMainActivity.goHome(this));

        // 인증받기 버튼
        btnRequestAuth.setOnClickListener(v -> requestAuthCode());

        // 인증번호를 입력하면 인증확인 버튼을 보여준다.
        etAuthCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!TextUtils.isEmpty(s.toString().trim())) {
                    btnVerifyCode.setVisibility(View.VISIBLE);
                } else {
                    btnVerifyCode.setVisibility(View.GONE);
                }

                // 인증 후 번호를 고치면 다시 인증받아야 한다.
                if (isCodeVerified) {
                    isCodeVerified = false;
                    btnVerifyCode.setText("인증확인");
                    btnVerifyCode.setEnabled(true);
                    containerNewPw.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 인증확인 버튼
        btnVerifyCode.setOnClickListener(v -> verifyAuthCode());

        // 새 비밀번호 보기/숨기기 토글
        btnTogglePw1.setOnClickListener(v -> {
            isPwVisible = !isPwVisible;
            if (isPwVisible) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePw1.setImageResource(R.drawable.ic_eye_on);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePw1.setImageResource(R.drawable.ic_eye_off);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        // 새 비밀번호 확인 보기/숨기기 토글
        btnTogglePw2.setOnClickListener(v -> {
            isPw2Visible = !isPw2Visible;
            if (isPw2Visible) {
                etPasswordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePw2.setImageResource(R.drawable.ic_eye_on);
            } else {
                etPasswordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePw2.setImageResource(R.drawable.ic_eye_off);
            }
            etPasswordConfirm.setSelection(etPasswordConfirm.getText().length());
        });

        // 비밀번호 변경 버튼
        btnConfirm.setOnClickListener(v -> resetPassword());
    }

    /** 입력한 이메일로 인증번호를 보낸다. */
    private void requestAuthCode() {
        String email = normalizeEmail();

        if (email == null) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다. (예: example@gmail.com)",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        etEmail.setText(email);
        etEmail.setSelection(email.length());

        // [여기에 DB 입력] 가입된 이메일인지 확인 (users)
        //                 없으면 "가입되지 않은 이메일입니다." 안내 후 return
        // [여기에 DB 입력] 발송한 인증번호를 만료시각과 함께 저장 (auth_codes)
        isCodeSent = true;
        Toast.makeText(this, email + " 로 인증번호가 발송되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /** 입력한 인증번호를 확인하고, 통과하면 새 비밀번호 입력란을 연다. */
    private void verifyAuthCode() {
        String authCode = etAuthCode.getText().toString().trim();

        if (!isCodeSent) {
            Toast.makeText(this, "먼저 인증받기를 눌러주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(authCode) || authCode.length() != AUTH_CODE_LENGTH) {
            Toast.makeText(this, "인증번호 " + AUTH_CODE_LENGTH + "자리를 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 저장된 인증번호와 입력값이 같은지, 만료되지 않았는지 확인 (auth_codes)
        //                 틀리거나 만료됐으면 안내 후 return
        isCodeVerified = true;
        btnVerifyCode.setText("인증완료");
        btnVerifyCode.setEnabled(false);

        containerNewPw.setVisibility(View.VISIBLE);
        Toast.makeText(this, "인증이 완료되었습니다. 새 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
    }

    /** 새 비밀번호로 변경한다. */
    private void resetPassword() {
        String pw = etPassword.getText().toString().trim();
        String pw2 = etPasswordConfirm.getText().toString().trim();

        if (!isCodeVerified) {
            Toast.makeText(this, "인증확인을 완료해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(pw) || pw.length() < PASSWORD_MIN_LENGTH) {
            Toast.makeText(this, "비밀번호는 " + PASSWORD_MIN_LENGTH + "자 이상 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pw.equals(pw2)) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 새 비밀번호 저장 - 암호화(해시)해서 저장 (users)
        // [여기에 DB 입력] 사용한 인증번호 만료 처리 (auth_codes)
        Toast.makeText(this, "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.",
                Toast.LENGTH_SHORT).show();

        // 비밀번호가 바뀌었으니 기존 로그인 상태는 지우고 로그인 화면으로 돌아간다.
        LoginSession.logout(this);
        LoginMainActivity.backToLogin(this);
    }

    /**
     * etEmail의 입력값을 정규화한 이메일 주소로 돌려준다.
     * 공백을 없애고 소문자로 바꾼 뒤 형식을 검사하며, 올바르지 않으면 null을 돌려준다.
     */
    private String normalizeEmail() {
        String input = etEmail.getText().toString().replaceAll("\\s", "");

        if (TextUtils.isEmpty(input)) {
            return null;
        }

        String email = input.toLowerCase(Locale.ROOT);

        // @가 없거나 두 개 이상이면 잘못된 주소
        if (email.indexOf('@') <= 0 || email.indexOf('@') != email.lastIndexOf('@')) {
            return null;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return null;
        }

        return email;
    }
}
