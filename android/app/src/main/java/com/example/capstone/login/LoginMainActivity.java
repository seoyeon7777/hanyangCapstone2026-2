package com.example.capstone.login;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.mypage.NoticeActivity;

public class LoginMainActivity extends AppCompatActivity {

    public static final String EXTRA_RETURN = "return_after_login";
    private static final String EXTRA_CLOSE = "close_login";

    private static boolean returnAfterLogin = false;

    private ImageButton btnBack, btnHome;
    private Button btnLoginPhone, btnLoginEmail;
    private ImageButton btnKakao, btnNaver, btnGmail;
    private TextView tvSignup, tvResetPw, tvFindId, tvContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_CLOSE, false)) {
            finish();
            return;
        }
        returnAfterLogin = getIntent().getBooleanExtra(EXTRA_RETURN, false);
        setContentView(R.layout.activity_login_main);

        initViews();
        setListeners();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_CLOSE, false)) {
            finish();
        }
    }

    public static Intent returnIntent(Context context) {
        Intent intent = new Intent(context, LoginMainActivity.class);
        intent.putExtra(EXTRA_RETURN, true);
        return intent;
    }

    public static void finishLogin(Activity activity) {
        if (!returnAfterLogin) {
            goToMyPage(activity);
            return;
        }
        returnAfterLogin = false;
        Toast.makeText(activity, "로그인되었습니다.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(activity, LoginMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_CLOSE, true);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void socialNotReady(Activity activity) {
        Toast.makeText(activity,
                "소셜 로그인은 준비 중이에요. 이메일 또는 휴대폰 번호로 로그인해 주세요.",
                Toast.LENGTH_SHORT).show();
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
        btnKakao.setOnClickListener(v -> socialNotReady(this));

        btnNaver.setOnClickListener(v -> socialNotReady(this));

        btnGmail.setOnClickListener(v -> socialNotReady(this));

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