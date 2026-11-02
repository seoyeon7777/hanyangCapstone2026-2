package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

public class PhoneVerifyActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;
    private EditText etPhone, etAuthCode;
    private Button btnVerify, btnVerifyCode, btnNext;

    private boolean isCodeSent = false;
    private boolean isCodeVerified = false;

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
            String phone = etPhone.getText().toString().trim();

            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(
                        this,
                        "휴대폰 번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            isCodeSent = true;

            Toast.makeText(
                    this,
                    "인증번호가 발송되었습니다.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        etAuthCode.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {

                if (!TextUtils.isEmpty(s.toString().trim())) {
                    btnVerifyCode.setVisibility(View.VISIBLE);
                } else {
                    btnVerifyCode.setVisibility(View.GONE);
                }

                if (isCodeVerified) {
                    isCodeVerified = false;
                    btnVerifyCode.setText("인증확인");
                    btnVerifyCode.setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnVerifyCode.setOnClickListener(v -> {

            String authCode =
                    etAuthCode.getText().toString().trim();

            if (!isCodeSent) {
                Toast.makeText(
                        this,
                        "먼저 인증받기를 눌러주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (TextUtils.isEmpty(authCode)
                    || authCode.length() != 4) {

                Toast.makeText(
                        this,
                        "인증번호 4자리를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            isCodeVerified = true;

            btnVerifyCode.setText("인증완료");
            btnVerifyCode.setEnabled(false);

            Toast.makeText(
                    this,
                    "인증이 완료되었습니다.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        btnNext.setOnClickListener(v -> {

            String phone =
                    etPhone.getText().toString().trim();

            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(
                        this,
                        "휴대폰 번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (!isCodeVerified) {
                Toast.makeText(
                        this,
                        "인증확인을 완료해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Intent intent =
                    new Intent(
                            PhoneVerifyActivity.this,
                            SigninActivity.class
                    );

            startActivity(intent);
        });
    }
}