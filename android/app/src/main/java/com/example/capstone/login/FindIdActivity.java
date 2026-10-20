package com.example.capstone.login;

import com.example.capstone.R;

import android.os.Bundle;
import android.text.Editable;
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

public class FindIdActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;

    private EditText etName, etPhone, etAuthCode;
    private Button btnRequestAuth, btnVerifyCode, btnConfirm;

    private LinearLayout containerResult;
    private TextView tvFoundAccount;

    private boolean isCodeSent = false;
    private boolean isCodeVerified = false;
    private boolean isAccountFound = false;

    /** 인증번호 자릿수 */
    private static final int AUTH_CODE_LENGTH = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_id);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etAuthCode = findViewById(R.id.etAuthCode);

        btnRequestAuth = findViewById(R.id.btnRequestAuth);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnConfirm = findViewById(R.id.btnConfirm);

        containerResult = findViewById(R.id.containerResult);
        tvFoundAccount = findViewById(R.id.tvFoundAccount);
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
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 인증확인 버튼
        btnVerifyCode.setOnClickListener(v -> verifyAuthCode());

        // 계정 찾기 버튼
        btnConfirm.setOnClickListener(v -> findAccount());
    }

    /** 입력한 휴대폰 번호로 인증번호를 보낸다. */
    private void requestAuthCode() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "휴대폰 번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 이름 + 휴대폰 번호가 일치하는 회원이 있는지 확인 (users)
        //                 없으면 "일치하는 회원 정보가 없습니다." 안내 후 return
        // [여기에 DB 입력] 발송한 인증번호를 만료시각과 함께 저장 (auth_codes)
        isCodeSent = true;
        Toast.makeText(this, "인증번호가 발송되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /** 입력한 인증번호를 확인한다. */
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
        Toast.makeText(this, "인증이 완료되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /** 본인확인을 마친 뒤 계정을 찾아 화면에 보여준다. */
    private void findAccount() {

        // 이미 계정을 찾은 뒤에는 로그인 화면으로 돌아간다.
        if (isAccountFound) {
            LoginMainActivity.backToLogin(this);
            return;
        }

        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "휴대폰 번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isCodeVerified) {
            Toast.makeText(this, "인증확인을 완료해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 이름 + 휴대폰 번호로 가입 이메일 조회 후 tvFoundAccount에 표시 (users)
        //                 이메일은 일부를 가려서 표시 (예: ex****@gmail.com)
        tvFoundAccount.setText("-");
        containerResult.setVisibility(View.VISIBLE);

        isAccountFound = true;
        btnConfirm.setText("로그인하러 가기");
        Toast.makeText(this, "계정을 찾았습니다.", Toast.LENGTH_SHORT).show();
    }
}
