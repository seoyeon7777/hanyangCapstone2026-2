package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

/** 비밀번호 재설정: 휴대폰 번호로 찾을지 이메일로 찾을지 고르는 화면 */
public class ResetPwMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_pw_main);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> LoginMainActivity.goHome(this));

        findViewById(R.id.btnResetPhone).setOnClickListener(v -> openReset(true));
        findViewById(R.id.btnResetEmail).setOnClickListener(v -> openReset(false));
    }

    private void openReset(boolean byPhone) {
        Intent intent = new Intent(this, ResetPwActivity.class);
        intent.putExtra(ResetPwActivity.EXTRA_BY_PHONE, byPhone);
        startActivity(intent);
    }
}
