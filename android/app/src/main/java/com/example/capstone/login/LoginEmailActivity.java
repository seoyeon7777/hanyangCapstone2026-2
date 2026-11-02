package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

public class LoginEmailActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;
    private EditText etPhone, etAuthCode;
    private Spinner spDomain;
    private Button btnCheckId, btnlogin;
    private ImageButton btnTogglePw;
    private TextView tvSignup;
    private ImageButton btnKakao, btnNaver, btnGmail;

    private boolean isPwVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_email);

        initViews();
        setupDomainSpinner();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etPhone = findViewById(R.id.etPhone);
        etAuthCode = findViewById(R.id.etAuthCode);
        btnTogglePw = findViewById(R.id.btnTogglePw);
        spDomain = findViewById(R.id.spDomain);

        btnCheckId = findViewById(R.id.btnCheckId);
        btnlogin = findViewById(R.id.btnlogin);

        tvSignup = findViewById(R.id.tvSignup);

        btnKakao = findViewById(R.id.btnKakao);
        btnNaver = findViewById(R.id.btnNaver);
        btnGmail = findViewById(R.id.btnGmail);
    }

    private void setupDomainSpinner() {
        String[] domainOptions = {
                "gmail.com",
                "naver.com",
                "daum.net",
                "hanmail.net",
                "nate.com"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                domainOptions
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spDomain.setAdapter(adapter);
    }

    private String buildFullEmail() {
        String id = etPhone.getText().toString().trim();

        String domain = spDomain.getSelectedItem() != null
                ? spDomain.getSelectedItem().toString()
                : "";

        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(domain)) {
            return "";
        }

        return id + "@" + domain;
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent =
                    new Intent(LoginEmailActivity.this, MainActivity.class);

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

            if (TextUtils.isEmpty(etPhone.getText().toString().trim())) {
                Toast.makeText(
                        this,
                        "아이디를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            String email = buildFullEmail();

            if (TextUtils.isEmpty(email)
                    || !android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()) {

                Toast.makeText(
                        this,
                        "올바른 이메일 형식이 아닙니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // [여기에 DB 입력] 가입된 이메일인지 확인 (users)
            Toast.makeText(
                    this,
                    email + " 로 인증 메일이 발송되었습니다.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        btnlogin.setOnClickListener(v -> {

            String email = buildFullEmail();
            String authCode =
                    etAuthCode.getText().toString().trim();

            if (TextUtils.isEmpty(email)
                    || !android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()) {

                Toast.makeText(
                        this,
                        "이메일 주소를 정확히 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (TextUtils.isEmpty(authCode)) {
                Toast.makeText(
                        this,
                        "비밀번호를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // [여기에 DB 입력] 이메일 + 비밀번호가 맞는 회원인지 확인 (users)
            //                 틀리면 "이메일 또는 비밀번호가 올바르지 않습니다." 안내 후 return
            //                 맞으면 회원 이름/휴대폰도 함께 받아 아래 login(...)에 넘긴다
            LoginSession.login(this, "", email, "");
            LoginMainActivity.goToMyPage(this);
        });

        tvSignup.setOnClickListener(v -> {
            Intent intent =
                    new Intent(
                            LoginEmailActivity.this,
                            SigninMainActivity.class
                    );

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