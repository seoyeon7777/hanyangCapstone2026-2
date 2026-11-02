package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

public class SigninMainActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;
    private Button btnSignup;
    private ImageButton btnNaver, btnFacebook, btnGmail;
    private TextView tvGoLogin, btLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin_main);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        btnSignup = findViewById(R.id.btnSignup);

        btnNaver = findViewById(R.id.btnNaver);
        btnFacebook = findViewById(R.id.btnFacebook);
        btnGmail = findViewById(R.id.btnGmail);

        tvGoLogin = findViewById(R.id.tvGoLogin);
        btLogin = findViewById(R.id.Btlogin);
    }

    private void setListeners() {

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 홈으로 이동
        btnHome.setOnClickListener(v -> {
            Intent intent =
                    new Intent(SigninMainActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        // 회원가입하기 → 휴대폰 본인인증
        btnSignup.setOnClickListener(v -> {
            Intent intent =
                    new Intent(SigninMainActivity.this, PhoneVerifyActivity.class);
            startActivity(intent);
        });

        // 소셜 회원가입/로그인 - 나중에 연결
        btnNaver.setOnClickListener(v -> {
        });

        btnFacebook.setOnClickListener(v -> {
        });

        btnGmail.setOnClickListener(v -> {
        });

        // 로그인하기 → 이전 로그인 화면으로 복귀
        btLogin.setOnClickListener(v -> finish());
    }
}