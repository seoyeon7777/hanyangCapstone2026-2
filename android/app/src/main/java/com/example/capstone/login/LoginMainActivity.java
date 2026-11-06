package com.example.capstone.login;

import android.app.Activity;
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
import com.example.capstone.data.LocalAccounts;

public class LoginMainActivity extends AppCompatActivity {

    private ImageButton btnBack, btnTogglePw;
    private EditText etId, etPassword;
    private Button btnLogin, btnSignup;
    private TextView tvResetPw, tvFindId;

    private boolean isPwVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_main);

        initViews();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        etId = findViewById(R.id.etId);
        etPassword = findViewById(R.id.etPassword);
        btnTogglePw = findViewById(R.id.btnTogglePw);
        btnLogin = findViewById(R.id.btnLogin);

        btnSignup = findViewById(R.id.btnSignup);
        tvResetPw = findViewById(R.id.tvResetPw);
        tvFindId = findViewById(R.id.tvFindId);
    }

    private void setListeners() {

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 비밀번호 보기/숨기기
        btnTogglePw.setOnClickListener(v -> {
            isPwVisible = !isPwVisible;

            etPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            | (isPwVisible
                            ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            : InputType.TYPE_TEXT_VARIATION_PASSWORD)
            );
            btnTogglePw.setImageResource(isPwVisible ? R.drawable.ic_eye_on : R.drawable.ic_eye_off);
            etPassword.setSelection(etPassword.getText().length());
        });

        // 로그인: 아이디 + 비밀번호 (인증 없음)
        btnLogin.setOnClickListener(v -> login());

        // 회원가입 → 휴대전화 본인인증부터
        btnSignup.setOnClickListener(v ->
                startActivity(new Intent(LoginMainActivity.this, PhoneVerifyActivity.class)));

        // 아이디 찾기
        tvFindId.setOnClickListener(v ->
                startActivity(new Intent(LoginMainActivity.this, FindIdActivity.class)));

        // 비밀번호 찾기(재설정)
        tvResetPw.setOnClickListener(v ->
                startActivity(new Intent(LoginMainActivity.this, ResetPwMainActivity.class)));
    }

    private void login() {
        String id = etId.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(id)) {
            Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 아이디 + 비밀번호가 맞는 회원인지 확인 (users)
        LocalAccounts.Account account = LocalAccounts.loginWithId(this, id, password);

        if (account == null) {
            Toast.makeText(this, "아이디 또는 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        LoginSession.login(this, account.id, account.name, account.email, account.phone);
        goToMyPage(this);
    }

    /**
     * 로그인 성공 후 호출. 쌓여 있던 로그인 화면들을 모두 닫고 홈 위에 마이페이지를 띄운다.
     * (마이페이지에서 뒤로가기 → 홈)
     */
    public static void goToMyPage(Activity activity) {
        // 찜·장바구니 때문에 로그인하러 온 경우: 마이페이지로 가지 않고 원래 화면으로 돌아간다
        if (LoginGate.hasPending()) {
            activity.finish();
            return;
        }

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