package com.example.capstone.login;

import com.example.capstone.R;
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.data.PasswordRule;

import android.os.Bundle;
import android.os.LocaleList;
import android.text.Editable;
import android.text.InputType;
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

import java.util.Locale;

public class ResetPwActivity extends AppCompatActivity {

    /** true면 휴대폰 번호로, false면 이메일로 찾는 화면 */
    public static final String EXTRA_BY_PHONE = "extra_by_phone";

    private boolean byPhone;

    private ImageButton btnBack, btnHome;

    private EditText etEmail, etAuthCode, etPassword, etPasswordConfirm;
    private Button btnRequestAuth, btnVerifyCode, btnConfirm;
    private ImageButton btnTogglePw1, btnTogglePw2;

    private LinearLayout containerNewPw;

    private final AuthCode authCode = new AuthCode();

    /** 인증번호를 보낸 아이디와, 그 아이디의 회원 정보 */
    private String sentId;
    private LocalAccounts.Account account;

    private boolean isCodeVerified = false;

    private boolean isPwVisible = false;
    private boolean isPw2Visible = false;

    /** 인증번호 자릿수 */
    private static final int AUTH_CODE_LENGTH = 4;
    /** 비밀번호 최소 길이 */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_pw);

        byPhone = getIntent().getBooleanExtra(EXTRA_BY_PHONE, false);

        initViews();
        applyContactType();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etEmail = findViewById(R.id.etEmail);
        etAuthCode = findViewById(R.id.etAuthCode);
        etPassword = findViewById(R.id.etPassword);
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm);

        btnRequestAuth = findViewById(R.id.btnRequestAuth);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnConfirm = findViewById(R.id.btnConfirm);

        btnTogglePw1 = findViewById(R.id.btnTogglePw1);
        btnTogglePw2 = findViewById(R.id.btnTogglePw2);

        containerNewPw = findViewById(R.id.containerNewPw);

        authCode.bindTimer(findViewById(R.id.tvTimer));
    }

    @Override
    protected void onDestroy() {
        authCode.clear();       // 남은 시간 갱신을 멈춘다
        super.onDestroy();
    }

    /** 아이디를 입력받는다. 휴대전화/이메일은 인증번호를 어디로 받을지만 정한다 */
    private void applyContactType() {
        ((TextView) findViewById(R.id.tvContactLabel)).setText("아이디");
        etEmail.setHint("가입한 아이디");
        // 아이디는 영문·숫자라 영문 키보드로 시작한다 (한글로 바꿀 수는 있다). 자동 수정은 끈다
        etEmail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etEmail.setImeHintLocales(new LocaleList(Locale.ENGLISH));
        ((TextView) findViewById(R.id.tvGuide)).setText(byPhone
                ? "아이디를 입력하면 가입한 휴대전화로 인증번호를 보내드려요."
                : "아이디를 입력하면 등록한 이메일로 인증번호를 보내드려요.");
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
                    containerNewPw.setVisibility(View.GONE);
                    setConfirmEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 인증확인 버튼
        btnVerifyCode.setOnClickListener(v -> verifyAuthCode());

        // 새 비밀번호 보기/숨기기 토글
        btnTogglePw1.setOnClickListener(v -> {
            isPwVisible = !isPwVisible;
            if (isPwVisible) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePw1.setImageResource(R.drawable.ic_eye_on);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePw1.setImageResource(R.drawable.ic_eye_off);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        // 새 비밀번호 확인 보기/숨기기 토글
        btnTogglePw2.setOnClickListener(v -> {
            isPw2Visible = !isPw2Visible;
            if (isPw2Visible) {
                etPasswordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnTogglePw2.setImageResource(R.drawable.ic_eye_on);
            } else {
                etPasswordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnTogglePw2.setImageResource(R.drawable.ic_eye_off);
            }
            etPasswordConfirm.setSelection(etPasswordConfirm.getText().length());
        });

        // 비밀번호 변경 버튼
        btnConfirm.setOnClickListener(v -> resetPassword());
        setConfirmEnabled(false);
    }

    /** 인증확인을 마쳐야 넘어갈 수 있다. 마치기 전에는 버튼을 눌러도 반응하지 않고 흐리게 보인다 */
    private void setConfirmEnabled(boolean enabled) {
        btnConfirm.setEnabled(enabled);
        btnConfirm.setAlpha(enabled ? 1f : 0.4f);
    }

    /** 인증 전 상태로 되돌린다 */
    private void resetFlow() {
        authCode.clear();
        sentId = null;
        account = null;
        isCodeVerified = false;
        etAuthCode.setText("");
        btnVerifyCode.setText("인증확인");
        btnVerifyCode.setEnabled(true);
        btnVerifyCode.setVisibility(View.GONE);
        containerNewPw.setVisibility(View.GONE);
        setConfirmEnabled(false);
        btnRequestAuth.setText("인증받기");
    }

    /** 입력한 아이디의 회원이 있으면, 그 회원의 휴대전화(또는 이메일)로 인증번호를 보낸다. */
    private void requestAuthCode() {
        String id = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(id)) {
            Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 가입된 아이디인지 확인 (users)
        LocalAccounts.Account found = LocalAccounts.findAccountById(this, id);
        if (found == null) {
            Toast.makeText(this, "존재하지 않는 아이디입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String target = byPhone ? found.phone : found.email;
        if (TextUtils.isEmpty(target)) {
            Toast.makeText(this, byPhone
                            ? "이 계정에 등록된 휴대전화 번호가 없습니다."
                            : "이 계정에 등록된 이메일이 없습니다. 휴대전화로 인증해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        resetFlow();

        // [여기에 DB 입력] 발송한 인증번호를 만료시각과 함께 저장 (auth_codes)
        account = found;
        sentId = id;
        authCode.issue(this, id, byPhone ? AuthCode.maskPhone(target) : AuthCode.maskEmail(target));
        btnRequestAuth.setText("재전송");      // 언제든 다시 받을 수 있고, 받으면 시간이 처음부터 시작된다
    }

    /** 입력한 인증번호를 확인하고, 통과하면 새 비밀번호 입력란을 연다. */
    private void verifyAuthCode() {
        String input = etAuthCode.getText().toString().trim();

        if (input.length() != AUTH_CODE_LENGTH) {
            Toast.makeText(this, "인증번호 " + AUTH_CODE_LENGTH + "자리를 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 칸의 아이디가 아니라 마지막으로 인증받기를 눌러 인증번호가 나간 아이디로 확인한다
        String error = authCode.check(input, sentId);

        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        isCodeVerified = true;
        authCode.hideTimer();       // 인증을 마쳤으니 남은 시간은 더 보여 줄 필요가 없다
        btnVerifyCode.setText("인증완료");
        btnVerifyCode.setEnabled(false);

        containerNewPw.setVisibility(View.VISIBLE);
        setConfirmEnabled(true);
        Toast.makeText(this, "인증이 완료되었습니다. 새 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
    }

    /** 새 비밀번호로 변경한다. */
    private void resetPassword() {
        String pw = etPassword.getText().toString().trim();
        String pw2 = etPasswordConfirm.getText().toString().trim();

        if (!isCodeVerified || account == null) {
            Toast.makeText(this, "인증확인을 완료해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 프로필 수정의 비밀번호 변경과 같은 규칙 (영문·숫자·특수문자 포함 8~20자)
        if (!PasswordRule.isValid(pw)) {
            Toast.makeText(this, PasswordRule.MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pw.equals(pw2)) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 새 비밀번호 저장 - 암호화(해시)해서 저장 (users)
        if (!LocalAccounts.changePassword(this, account.id, pw)) {
            Toast.makeText(this, "존재하지 않는 아이디입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 사용한 인증번호 만료 처리 (auth_codes)
        authCode.clear();
        Toast.makeText(this, "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.",
                Toast.LENGTH_SHORT).show();

        // 비밀번호가 바뀌었으니 기존 로그인 상태는 지우고 로그인 화면으로 돌아간다.
        LoginSession.logout(this);
        LoginMainActivity.backToLogin(this);
    }
}
