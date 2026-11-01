package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.AccountStore;
import com.example.capstone.data.AuthCodes;
import com.example.capstone.data.InputRules;

public class PhoneVerifyActivity extends AppCompatActivity {

    public static final String EXTRA_PHONE = "verified_phone";

    private ImageButton btnBack, btnHome;
    private EditText etPhone, etAuthCode;
    private Button btnVerify, btnVerifyCode, btnNext;
    private TextView tvVerifyStatus;

    private String sentPhone = null;
    private String verifiedPhone = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_verify);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etPhone = findViewById(R.id.etPhone);
        etAuthCode = findViewById(R.id.etAuthCode);

        btnVerify = findViewById(R.id.btnVerify);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnNext = findViewById(R.id.btnNext);
        tvVerifyStatus = findViewById(R.id.tvVerifyStatus);
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent =
                    new Intent(PhoneVerifyActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnVerify.setOnClickListener(v -> {
            String phone = InputRules.normalizePhone(etPhone.getText().toString());

            if (phone == null) {
                Toast.makeText(
                        this,
                        "휴대폰 번호를 정확히 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (AccountStore.phoneExists(this, phone)) {
                Toast.makeText(
                        this,
                        "이미 가입된 휴대폰 번호입니다. 로그인하거나 계정 찾기를 이용해주세요.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            etPhone.setText(phone);
            etPhone.setSelection(phone.length());

            sentPhone = phone;
            verifiedPhone = null;
            String code = AuthCodes.issue(phone);

            btnVerify.setText("다시 받기");
            setVerifyEnabled(true);
            btnVerifyCode.setText("인증확인");
            tvVerifyStatus.setText("인증번호를 보냈어요. 3분 안에 입력해주세요.");
            etAuthCode.setText("");
            etAuthCode.requestFocus();

            Toast.makeText(
                    this,
                    AuthCodes.sentMessage(code),
                    Toast.LENGTH_LONG
            ).show();
        });

        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String phone = InputRules.normalizePhone(s.toString());
                if (sentPhone != null && !sentPhone.equals(phone)) {
                    sentPhone = null;
                    verifiedPhone = null;
                    btnVerify.setText("인증받기");
                    btnVerifyCode.setText("인증확인");
                    setVerifyEnabled(false);
                    tvVerifyStatus.setText("번호가 바뀌었어요. 인증번호를 다시 받아주세요.");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnVerifyCode.setOnClickListener(v -> {

            String authCode =
                    etAuthCode.getText().toString().trim();

            if (sentPhone == null) {
                Toast.makeText(
                        this,
                        "먼저 인증받기를 눌러주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (TextUtils.isEmpty(authCode)
                    || authCode.length() != InputRules.AUTH_CODE_LENGTH) {

                Toast.makeText(
                        this,
                        "인증번호 " + InputRules.AUTH_CODE_LENGTH + "자리를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            AuthCodes.Result result = AuthCodes.verify(sentPhone, authCode);
            Toast.makeText(this, AuthCodes.message(result), Toast.LENGTH_SHORT).show();

            if (result != AuthCodes.Result.OK) {
                if (result == AuthCodes.Result.EXPIRED) {
                    tvVerifyStatus.setText("인증 시간이 지났어요. 다시 받기를 눌러주세요.");
                }
                return;
            }

            verifiedPhone = sentPhone;
            btnVerifyCode.setText("인증완료");
            setVerifyEnabled(false);
            tvVerifyStatus.setText("✓ " + verifiedPhone + " 인증이 완료되었어요.");
        });

        btnNext.setOnClickListener(v -> {

            if (verifiedPhone == null) {
                Toast.makeText(
                        this,
                        "휴대폰 인증을 완료해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Intent intent =
                    new Intent(
                            PhoneVerifyActivity.this,
                            SigninActivity.class
                    );
            intent.putExtra(EXTRA_PHONE, verifiedPhone);

            startActivity(intent);
        });
    }

    private void setVerifyEnabled(boolean enabled) {
        btnVerifyCode.setEnabled(enabled);
        btnVerifyCode.setAlpha(enabled ? 1f : 0.4f);
        etAuthCode.setEnabled(enabled);
    }
}
