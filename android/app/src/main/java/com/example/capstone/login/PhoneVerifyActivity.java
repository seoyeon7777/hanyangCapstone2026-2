package com.example.capstone.login;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.ui.AppDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.security.SecureRandom;
import java.util.Locale;

/** 회원가입 본인확인: 010 - XXXX - XXXX 휴대폰 번호로 인증한다 */
public class PhoneVerifyActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;
    private EditText etPhoneMid, etPhoneLast, etAuthCode;
    private TextView tvAuthCodeNotice, tvNoAuthCode;
    // btnVerifyCode = 인증번호 확인 + 다음 화면으로 ("다음")
    private Button btnVerify, btnVerifyCode;

    private boolean isCodeSent = false;
    private boolean isCodeVerified = false;

    // 문자 발송 서비스(서버)가 생기면 서버가 번호로 보내고, 앱은 번호를 모르게 한다
    private String sentCode = "";

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

        etPhoneMid = findViewById(R.id.etPhoneMid);
        etPhoneLast = findViewById(R.id.etPhoneLast);
        etAuthCode = findViewById(R.id.etAuthCode);
        tvAuthCodeNotice = findViewById(R.id.tvAuthCodeNotice);
        tvNoAuthCode = findViewById(R.id.tvNoAuthCode);
        tvNoAuthCode.setPaintFlags(tvNoAuthCode.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        btnVerify = findViewById(R.id.btnVerify);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
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

        setPhoneListeners();

        tvNoAuthCode.setOnClickListener(v -> showNoAuthCodeHelp());

        btnVerify.setOnClickListener(v -> {
            String phone = checkedPhone();

            if (phone == null) {
                return;
            }

            // 다시 받으면 새 번호를 만들고 인증도 처음부터
            resetVerification();
            sentCode = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
            isCodeSent = true;
            // 한 번 보낸 뒤로는 누를 때마다 새 인증번호
            btnVerify.setText("다시 요청");

            tvAuthCodeNotice.setText(
                    "인증번호: " + sentCode + " (문자 발송 기능 준비 중이라 화면에 보여 드려요)");
            tvAuthCodeNotice.setVisibility(View.VISIBLE);

            etAuthCode.setText("");
            etAuthCode.requestFocus();

            Toast.makeText(
                    this,
                    phone + " (으)로 인증번호가 발송되었습니다.",
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

                if (isCodeVerified) {
                    isCodeVerified = false;
                    setPhoneLocked(false);
                }

                updateNextButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 인증번호 4자리를 다 넣어야 눌린다. 하나라도 다르면 안내 창, 같으면 다음 화면
        btnVerifyCode.setOnClickListener(v -> {

            String authCode =
                    etAuthCode.getText().toString().trim();

            if (!isCodeSent || !authCode.equals(sentCode)) {
                AppDialog.notice(
                        this,
                        "인증번호가 올바르지 않습니다.\n다시 입력해 주세요.",
                        "확인"
                );
                return;
            }

            String phone = checkedPhone();

            if (phone == null) {
                return;
            }

            isCodeVerified = true;
            setPhoneLocked(true);

            Intent intent =
                    new Intent(
                            PhoneVerifyActivity.this,
                            SigninActivity.class
                    );

            intent.putExtra(SigninActivity.EXTRA_PHONE, phone);

            startActivity(intent);
        });
    }

    /** 가운데 칸 4자리를 채우면 끝 칸으로, 끝 칸이 빈 채로 지우면 가운데 칸으로. 번호가 바뀌면 인증 초기화 */
    private void setPhoneListeners() {
        etPhoneMid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetVerification();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4 && etPhoneMid.hasFocus()) {
                    etPhoneLast.requestFocus();
                    etPhoneLast.setSelection(etPhoneLast.length());
                }
            }
        });

        etPhoneLast.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetVerification();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etPhoneLast.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && etPhoneLast.length() == 0) {
                etPhoneMid.requestFocus();
                etPhoneMid.setSelection(etPhoneMid.length());
                return true;
            }
            return false;
        });
    }

    /**
     * 입력한 번호를 010-XXXX-XXXX 모양으로 돌려준다.
     * 010 + 숫자 7~8자리(가운데 3~4, 끝 4)만 받는다. 틀렸거나 이미 가입된 번호면 안내하고 null
     */
    private String checkedPhone() {
        String mid = etPhoneMid.getText().toString().trim();
        String last = etPhoneLast.getText().toString().trim();

        if (TextUtils.isEmpty(mid) || TextUtils.isEmpty(last)) {
            toast("휴대폰 번호를 입력해주세요.");
            return null;
        }

        if (!mid.matches("[0-9]{3,4}") || !last.matches("[0-9]{4}")) {
            toast("휴대폰 번호를 정확히 입력해주세요. (010-0000-0000)");
            return null;
        }

        String phone = LocalAccounts.normalizePhone("010" + mid + last);

        if (phone == null) {
            toast("휴대폰 번호를 정확히 입력해주세요. (010-0000-0000)");
            return null;
        }

        // [여기에 DB 입력] 이미 가입된 휴대폰 번호인지 확인 (users)
        if (LocalAccounts.isTaken(this, "", phone)) {
            toast("이미 가입된 휴대폰 번호입니다.");
            return null;
        }

        return phone;
    }

    /** 번호를 바꾸면 보낸 인증번호와 인증 완료를 모두 지운다 → 인증받기부터 다시 */
    private void resetVerification() {
        if (!isCodeSent && !isCodeVerified) {
            return;
        }
        isCodeSent = false;
        isCodeVerified = false;
        sentCode = "";

        tvAuthCodeNotice.setVisibility(View.GONE);
        btnVerify.setText("인증받기");
        etAuthCode.setText("");
        updateNextButton();
    }

    /** 인증번호를 받고 4자리를 다 넣었을 때만 "다음"을 검은색으로 켠다 */
    private void updateNextButton() {
        boolean ready = isCodeSent && etAuthCode.getText().toString().trim().length() == 4;
        btnVerifyCode.setEnabled(ready);
        btnVerifyCode.setBackgroundResource(ready ? R.drawable.bg_button : R.drawable.bg_button_gray);
    }

    /** 인증이 끝나면 번호 칸과 인증받기 버튼을 막고 버튼을 회색으로 바꾼다 */
    private void setPhoneLocked(boolean locked) {
        etPhoneMid.setEnabled(!locked);
        etPhoneLast.setEnabled(!locked);
        int textColor = locked ? 0xFF888888 : 0xFF000000;
        etPhoneMid.setTextColor(textColor);
        etPhoneLast.setTextColor(textColor);

        btnVerify.setEnabled(!locked);
        btnVerify.setBackgroundResource(locked ? R.drawable.bg_button_gray : R.drawable.bg_button);
    }

    /** "인증번호가 안 와요." → 수신차단 확인 안내 */
    private void showNoAuthCodeHelp() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_auth_code_help, null, false);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        view.findViewById(R.id.btnCloseSheet).setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
