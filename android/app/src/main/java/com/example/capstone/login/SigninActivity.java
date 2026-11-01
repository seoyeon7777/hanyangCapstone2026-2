package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.AccountStore;
import com.example.capstone.data.InputRules;

public class SigninActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;

    private EditText etId, etPw, etPw2, etName, etNickname, etYear;
    private Button btnCheckId, btnNext;
    private ImageButton btnTogglePw1, btnTogglePw2;

    private Spinner spMonth, spDay;

    private CheckBox cbAll, cb1, cb2, cb3, cb4, cb5;

    private String checkedEmail = null;
    private boolean isPwVisible = false;
    private boolean isPw2Visible = false;

    private String verifiedPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        verifiedPhone = InputRules.normalizePhone(
                getIntent().getStringExtra(PhoneVerifyActivity.EXTRA_PHONE));

        if (verifiedPhone == null) {
            Toast.makeText(this, "휴대폰 인증부터 진행해주세요.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, PhoneVerifyActivity.class));
            finish();
            return;
        }

        initViews();
        setupSpinners();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHome = findViewById(R.id.btnHome);

        etId = findViewById(R.id.etId);
        btnCheckId = findViewById(R.id.btnCheckId);

        etPw = findViewById(R.id.etPw);
        btnTogglePw1 = findViewById(R.id.btnTogglePw1);

        etPw2 = findViewById(R.id.etPw2);
        btnTogglePw2 = findViewById(R.id.btnTogglePw);

        etName = findViewById(R.id.etName);
        etNickname = findViewById(R.id.etNickname);
        etYear = findViewById(R.id.etYear);

        spMonth = findViewById(R.id.spMonth);
        spDay = findViewById(R.id.spDay);

        cbAll = findViewById(R.id.cbAll);
        cb1 = findViewById(R.id.cb1);
        cb2 = findViewById(R.id.cb2);
        cb3 = findViewById(R.id.cb3);
        cb4 = findViewById(R.id.cb4);
        cb5 = findViewById(R.id.cb5);

        btnNext = findViewById(R.id.btnNext);

        InputFilter noSpace = (source, start, end, dest, dstart, dend) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isWhitespace(c)) {
                    sb.append(c);
                }
            }
            return sb.length() == end - start ? null : sb.toString();
        };
        InputFilter pwLength = new InputFilter.LengthFilter(InputRules.PASSWORD_MAX);
        etPw.setFilters(new InputFilter[]{noSpace, pwLength});
        etPw2.setFilters(new InputFilter[]{noSpace, pwLength});
        etNickname.setFilters(new InputFilter[]{noSpace,
                new InputFilter.LengthFilter(InputRules.NICKNAME_MAX)});
        etId.setFilters(new InputFilter[]{noSpace});
    }

    private void setupSpinners() {

        String[] months = new String[12];

        for (int i = 0; i < 12; i++) {
            months[i] = (i + 1) + "월";
        }

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                months
        );

        monthAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spMonth.setAdapter(monthAdapter);

        updateDays();

        spMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateDays();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        etYear.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    updateDays();
                }
            }
        });
    }

    private int selectedYear() {
        try {
            return Integer.parseInt(etYear.getText().toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void updateDays() {
        int year = selectedYear();
        int month = spMonth.getSelectedItemPosition() + 1;
        int days = InputRules.daysInMonth(year > 0 ? year : 2000, month);

        int previous = spDay.getAdapter() == null ? 0 : spDay.getSelectedItemPosition();
        if (spDay.getAdapter() != null && spDay.getAdapter().getCount() == days) {
            return;
        }

        String[] items = new String[days];
        for (int i = 0; i < days; i++) {
            items[i] = (i + 1) + "일";
        }

        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                items
        );

        dayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spDay.setAdapter(dayAdapter);
        spDay.setSelection(Math.min(Math.max(previous, 0), days - 1));
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent =
                    new Intent(SigninActivity.this, MainActivity.class);

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(intent);
            finish();
        });

        // 아이디 중복확인
        btnCheckId.setOnClickListener(v -> {

            String email = InputRules.normalizeEmail(etId.getText().toString());

            if (TextUtils.isEmpty(etId.getText().toString().trim())) {
                Toast.makeText(
                        this,
                        "이메일을 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (email == null) {
                Toast.makeText(
                        this,
                        "이메일 형식이 올바르지 않습니다. (예: example@gmail.com)",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // [여기에 DB 입력] 같은 아이디로 가입한 회원이 있는지 확인 (users)
            //                 있으면 "이미 사용 중인 아이디입니다." 안내 후 return
            if (AccountStore.emailExists(this, email)) {
                Toast.makeText(
                        this,
                        "이미 가입된 이메일입니다.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            etId.setText(email);
            etId.setSelection(email.length());
            checkedEmail = email;

            Toast.makeText(
                    this,
                    "사용 가능한 이메일입니다.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        etId.addTextChangedListener(new android.text.TextWatcher() {

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
                if (checkedEmail != null
                        && !checkedEmail.equals(InputRules.normalizeEmail(s.toString()))) {
                    checkedEmail = null;
                }
            }

            @Override
            public void afterTextChanged(
                    android.text.Editable s
            ) {
            }
        });

        // 비밀번호 보기/숨기기
        btnTogglePw1.setOnClickListener(v -> {

            isPwVisible = !isPwVisible;

            if (isPwVisible) {

                etPw.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                );

                btnTogglePw1.setImageResource(
                        R.drawable.ic_eye_on
                );

            } else {

                etPw.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_PASSWORD
                );

                btnTogglePw1.setImageResource(
                        R.drawable.ic_eye_off
                );
            }

            etPw.setSelection(
                    etPw.getText().length()
            );
        });

        // 비밀번호 재확인 보기/숨기기
        btnTogglePw2.setOnClickListener(v -> {

            isPw2Visible = !isPw2Visible;

            if (isPw2Visible) {

                etPw2.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                );

                btnTogglePw2.setImageResource(
                        R.drawable.ic_eye_on
                );

            } else {

                etPw2.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_PASSWORD
                );

                btnTogglePw2.setImageResource(
                        R.drawable.ic_eye_off
                );
            }

            etPw2.setSelection(
                    etPw2.getText().length()
            );
        });

        // 전체 동의
        cbAll.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    cb1.setChecked(isChecked);
                    cb2.setChecked(isChecked);
                    cb3.setChecked(isChecked);
                    cb4.setChecked(isChecked);
                    cb5.setChecked(isChecked);
                }
        );

        android.widget.CompoundButton.OnCheckedChangeListener individualListener =
                (buttonView, isChecked) -> updateAllCheckState();

        cb1.setOnCheckedChangeListener(individualListener);
        cb2.setOnCheckedChangeListener(individualListener);
        cb3.setOnCheckedChangeListener(individualListener);
        cb4.setOnCheckedChangeListener(individualListener);
        cb5.setOnCheckedChangeListener(individualListener);

        // 가입하기
        btnNext.setOnClickListener(v -> signup());
    }

    private void signup() {

        String pw = etPw.getText().toString();
        String pw2 = etPw2.getText().toString();
        String name = InputRules.normalizeName(etName.getText().toString());
        String nickname = InputRules.normalizeNickname(etNickname.getText().toString());
        int year = selectedYear();
        int month = spMonth.getSelectedItemPosition() + 1;
        int day = spDay.getSelectedItemPosition() + 1;

        if (checkedEmail == null) {
            toast(InputRules.normalizeEmail(etId.getText().toString()) == null
                    ? "이메일을 정확히 입력해주세요."
                    : "이메일 중복확인을 해주세요.");
            return;
        }

        if (!InputRules.isValidPassword(pw)) {
            toast(InputRules.PASSWORD_ERROR);
            return;
        }

        if (!pw.equals(pw2)) {
            toast("비밀번호가 일치하지 않습니다.");
            return;
        }

        if (name == null) {
            toast("이름을 입력해주세요. (" + InputRules.NAME_MAX + "자 이하)");
            return;
        }

        if (nickname == null) {
            toast(InputRules.NICKNAME_ERROR);
            return;
        }

        if (!InputRules.isValidBirthYear(year)) {
            toast("출생년도를 정확히 입력해주세요. (" + InputRules.BIRTH_YEAR_MIN + "~"
                    + InputRules.currentYear() + ")");
            return;
        }

        if (!InputRules.isValidBirth(year, month, day)) {
            toast("생년월일을 정확히 선택해주세요.");
            return;
        }

        if (InputRules.age(year, month, day) < 14) {
            toast("만 14세 이상만 가입할 수 있습니다.");
            return;
        }

        if (!cb1.isChecked()
                || !cb2.isChecked()
                || !cb3.isChecked()) {

            toast("필수 약관에 모두 동의해주세요.");
            return;
        }

        // [여기에 DB 입력] 회원가입 정보 저장 - 아이디, 비밀번호(암호화), 이름, 생년월일, 성별, 휴대폰 (users)
        //                 저장에 성공했을 때만 아래 완료 화면으로 이동할 것
        AccountStore.Account account = AccountStore.register(
                this,
                checkedEmail,
                verifiedPhone,
                pw,
                name,
                nickname,
                InputRules.formatBirth(year, month, day),
                "F"
        );

        if (account == null) {
            if (AccountStore.phoneExists(this, verifiedPhone)) {
                toast("이미 가입된 휴대폰 번호입니다.");
            } else if (AccountStore.emailExists(this, checkedEmail)) {
                checkedEmail = null;
                toast("이미 가입된 이메일입니다.");
            } else {
                toast("회원가입에 실패했어요. 입력한 정보를 다시 확인해주세요.");
            }
            return;
        }

        LoginSession.login(this, account);

        String accountKey = com.example.capstone.data.BodyProfileStore.accountKey(this);
        getSharedPreferences("profile_settings", MODE_PRIVATE).edit()
                .putBoolean("marketing_sms_" + accountKey, cb4.isChecked())
                .putBoolean("marketing_email_" + accountKey, cb4.isChecked())
                .apply();

        Intent intent =
                new Intent(
                        SigninActivity.this,
                        SigninCompleteActivity.class
                );

        intent.putExtra(
                SigninCompleteActivity.EXTRA_NAME,
                account.displayName()
        );

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateAllCheckState() {

        boolean allChecked =
                cb1.isChecked()
                        && cb2.isChecked()
                        && cb3.isChecked()
                        && cb4.isChecked()
                        && cb5.isChecked();

        cbAll.setOnCheckedChangeListener(null);
        cbAll.setChecked(allChecked);

        cbAll.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    cb1.setChecked(isChecked);
                    cb2.setChecked(isChecked);
                    cb3.setChecked(isChecked);
                    cb4.setChecked(isChecked);
                    cb5.setChecked(isChecked);
                }
        );
    }
}
