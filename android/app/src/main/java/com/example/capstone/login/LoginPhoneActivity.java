package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

public class LoginPhoneActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;
    private EditText etPhone, etAuthCode;
    private Button btnCheckId, btnlogin;
    private ImageButton btnTogglePw;
    private TextView tvSignup;
    private ImageButton btnKakao, btnNaver, btnGmail;

    private boolean isPwVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_phone);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etPhone = findViewById(R.id.etPhone);
        etAuthCode = findViewById(R.id.etAuthCode);
        btnTogglePw = findViewById(R.id.btnTogglePw);

        btnCheckId = findViewById(R.id.btnCheckId);
        btnlogin = findViewById(R.id.btnlogin);

        tvSignup = findViewById(R.id.tvSignup);

        btnKakao = findViewById(R.id.btnKakao);
        btnNaver = findViewById(R.id.btnNaver);
        btnGmail = findViewById(R.id.btnGmail);
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(LoginPhoneActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnTogglePw.setOnClickListener(v -> {
            isPwVisible = !isPwVisible;

            if (isPwVisible) {
                etAuthCode.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                );
                btnTogglePw.setImageResource(R.drawable.ic_eye_on);
            } else {
                etAuthCode.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_PASSWORD
                );
                btnTogglePw.setImageResource(R.drawable.ic_eye_off);
            }

            etAuthCode.setSelection(etAuthCode.getText().length());
        });

        btnCheckId.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();

            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(
                        this,
                        "휴대폰 번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // [여기에 DB 입력] 가입된 휴대폰 번호인지 확인 후 인증번호 발송 (users)
            Toast.makeText(
                    this,
                    "인증번호가 발송되었습니다.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        btnlogin.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String password = etAuthCode.getText().toString().trim();

            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(
                        this,
                        "휴대폰 번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                Toast.makeText(
                        this,
                        "비밀번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // [여기에 DB 입력] 휴대폰 번호 + 비밀번호가 맞는 회원인지 확인 (users)
            //                 틀리면 "휴대폰 번호 또는 비밀번호가 올바르지 않습니다." 안내 후 return
            //                 맞으면 회원 이름/이메일도 함께 받아 아래 login(...)에 넘긴다
            LoginSession.login(this, "", "", phone);
            LoginMainActivity.goToMyPage(this);
        });

        tvSignup.setOnClickListener(v -> {
            Intent intent =
                    new Intent(LoginPhoneActivity.this, SigninMainActivity.class);
            startActivity(intent);
        });

        btnKakao.setOnClickListener(v -> {
        });

        btnNaver.setOnClickListener(v -> {
        });

        btnGmail.setOnClickListener(v -> {
        });
    }
}