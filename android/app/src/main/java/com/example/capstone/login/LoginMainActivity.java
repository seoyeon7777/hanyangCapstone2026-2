package com.example.capstone.login;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.mypage.NoticeActivity;

public class LoginMainActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;
    private Button btnLoginPhone, btnLoginEmail;
    private ImageButton btnKakao, btnNaver, btnGmail;
    private TextView tvSignup, tvResetPw, tvFindId, tvContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_main);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        btnLoginPhone = findViewById(R.id.btnLoginPhone);
        btnLoginEmail = findViewById(R.id.btnLoginEmail);

        btnKakao = findViewById(R.id.btnKakao);
        btnNaver = findViewById(R.id.btnNaver);
        btnGmail = findViewById(R.id.btnGmail);

        tvSignup = findViewById(R.id.tvSignup);
        tvResetPw = findViewById(R.id.tvResetPw);
        tvFindId = findViewById(R.id.tvFindId);
        tvContact = findViewById(R.id.tvContact);
    }

    private void setListeners() {

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 홈으로 이동
        btnHome.setOnClickListener(v -> goHome(this));

        // 핸드폰 번호로 로그인
        btnLoginPhone.setOnClickListener(v -> {
            Intent intent =
                    new Intent(LoginMainActivity.this, LoginPhoneActivity.class);
            startActivity(intent);
        });

        // 이메일로 로그인
        btnLoginEmail.setOnClickListener(v -> {
            Intent intent =
                    new Intent(LoginMainActivity.this, LoginEmailActivity.class);
            startActivity(intent);
        });

        // 소셜 로그인 - 나중에 연동
        btnKakao.setOnClickListener(v -> {
        });

        btnNaver.setOnClickListener(v -> {
        });

        btnGmail.setOnClickListener(v -> {
        });

        // 회원가입
        tvSignup.setOnClickListener(v -> {
            Intent intent =
                    new Intent(LoginMainActivity.this, SigninMainActivity.class);
            startActivity(intent);
        });

        // 비밀번호 재설정
        tvResetPw.setOnClickListener(v ->
                startActivity(new Intent(LoginMainActivity.this, ResetPwActivity.class)));

        // 계정 찾기
        tvFindId.setOnClickListener(v ->
                startActivity(new Intent(LoginMainActivity.this, FindIdActivity.class)));

        // 문의하기 → 고객센터 (로그인 전이라 1:1 문의 대신 전화/FAQ 화면)
        tvContact.setOnClickListener(v ->
                startActivity(new Intent(LoginMainActivity.this, NoticeActivity.class)));
    }

    /**
     * 로그인 성공 후 호출. 쌓여 있던 로그인 화면들을 모두 닫고 홈 위에 마이페이지를 띄운다.
     * (마이페이지에서 뒤로가기 → 홈)
     */
    public static void goToMyPage(Activity activity) {
        Intent home = new Intent(activity, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Intent myPage = new Intent(activity, MyPageActivity.class);

        activity.startActivities(new Intent[]{home, myPage});
        activity.finish();
    }

    /** 아이디 찾기 / 비밀번호 재설정을 마친 뒤 로그인 화면으로 돌아온다. */
    public static void backToLogin(Activity activity) {
        Intent intent = new Intent(activity, LoginMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    /** 홈 버튼: 쌓여 있던 화면을 닫고 홈으로 돌아간다. */
    public static void goHome(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}