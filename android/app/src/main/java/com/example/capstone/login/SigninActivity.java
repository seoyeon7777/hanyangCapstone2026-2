package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.TextUtils;
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
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.ui.EmailDomainInput;

import java.util.Calendar;

public class SigninActivity extends AppCompatActivity {

    public static final String EXTRA_PHONE = "extra_phone";

    private static final String ID_RULE_MESSAGE =
            "아이디는 영문 소문자와 숫자를 포함해 4~16자로 입력해주세요.";
    private static final String PASSWORD_RULE_MESSAGE =
            "비밀번호는 영문 대소문자, 숫자, 특수문자 중 2가지 이상을 조합해 8~16자로 입력해주세요.";
    private static final String PASSWORD_CHAR_MESSAGE =
            "비밀번호에 쓸 수 없는 문자가 있습니다. 특수문자는 안내된 것만 쓸 수 있어요.";

    private ImageButton btnBack, btnHome;

    /** 생년월일 연도 목록의 가장 이른 해 (목록은 올해부터 이 해까지) */
    private static final int BIRTH_YEAR_MIN = 1940;
    private static final String YEAR_PLACEHOLDER = "년도";
    private static final String MONTH_PLACEHOLDER = "월";
    private static final String DAY_PLACEHOLDER = "일";
    private static final int CHECK_RED = 0xFFE5484D;
    private static final int CHECK_GREEN = 0xFF2EAE4F;
    private EditText etId, etPw, etPw2, etName, etPhone;
    private EmailDomainInput emailInput;
    private Button btnCheckId, btnNext;
    private ImageButton btnTogglePw1, btnTogglePw2;
    private android.widget.ImageView ivPwCheck, ivPw2Check;

    private Spinner spYear, spMonth, spDay, spGender;

    private CheckBox cbAll, cb1, cb2, cb3, cb4, cb5;

    private boolean isIdChecked = false;
    /** 서버에서 도메인 확인을 마친 이메일 (같은 주소로 다시 묻지 않는다) */
    private String domainCheckedEmail = "";
    private boolean isPwVisible = false;
    private boolean isPw2Visible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

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

        // 본인확인 화면에서 인증한 번호. 고칠 수 없다
        etPhone = findViewById(R.id.etPhone);
        etPhone.setText(getIntent().getStringExtra(EXTRA_PHONE));

        spYear = findViewById(R.id.spYear);
        spMonth = findViewById(R.id.spMonth);
        spDay = findViewById(R.id.spDay);
        spGender = findViewById(R.id.spGender);

        cbAll = findViewById(R.id.cbAll);
        cb1 = findViewById(R.id.cb1);
        cb2 = findViewById(R.id.cb2);
        cb3 = findViewById(R.id.cb3);
        cb4 = findViewById(R.id.cb4);
        cb5 = findViewById(R.id.cb5);

        btnNext = findViewById(R.id.btnNext);

        // 비밀번호 규칙 문구가 길어서 안내 글씨만 작게 (입력 글씨는 그대로). 아이디도 같은 크기
        smallHint(etId);
        smallHint(etPw);
        smallHint(etPw2);

        // 비밀번호 칸 옆 체크 표시: 입력을 시작하면 보이고, 비밀번호는 규칙에 맞을 때,
        // 재확인은 위 비밀번호와 똑같을 때 초록색 (아니면 빨간색)
        ivPwCheck = findViewById(R.id.ivPwCheck);
        ivPw2Check = findViewById(R.id.ivPw2Check);
        android.text.TextWatcher pwWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updatePwChecks();
            }
        };
        etPw.addTextChangedListener(pwWatcher);
        etPw2.addTextChangedListener(pwWatcher);
    }

    private void updatePwChecks() {
        String pw = etPw.getText().toString();
        String pw2 = etPw2.getText().toString();
        showCheck(etPw, ivPwCheck, !pw.isEmpty(), LocalAccounts.isValidPassword(pw.trim()));
        showCheck(etPw2, ivPw2Check, !pw2.isEmpty(), pw2.equals(pw));
    }

    /** 체크가 보일 때만 오른쪽 여백을 넓힌다 (비어 있을 때는 긴 안내 문구가 잘리지 않게) */
    private void showCheck(EditText field, android.widget.ImageView icon, boolean visible, boolean ok) {
        icon.setVisibility(visible ? View.VISIBLE : View.GONE);
        // 맞으면 초록 체크, 아니면 빨간 X
        icon.setImageResource(ok ? R.drawable.ic_pw_ok : R.drawable.ic_pw_bad);
        icon.setColorFilter(ok ? CHECK_GREEN : CHECK_RED);
        int endDp = visible ? 72 : 48;
        field.setPaddingRelative(field.getPaddingStart(), field.getPaddingTop(),
                Math.round(endDp * getResources().getDisplayMetrics().density), field.getPaddingBottom());
    }

    private static void smallHint(EditText editText) {
        SpannableString hint = new SpannableString(editText.getHint());
        hint.setSpan(new AbsoluteSizeSpan(12, true), 0, hint.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editText.setHint(hint);
    }

    private void setupSpinners() {

        // 연도: "년도" 다음에 올해부터 1940년까지 (최근 해가 위)
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        String[] years = new String[thisYear - BIRTH_YEAR_MIN + 2];
        years[0] = YEAR_PLACEHOLDER;
        for (int y = thisYear, i = 1; y >= BIRTH_YEAR_MIN; y--, i++) {
            years[i] = y + "년";
        }
        spYear.setAdapter(placeholderAdapter(years));

        // 월·일: 처음에는 "월"/"일"만 보이고, 펼친 목록에는 1월~12월, 1일~마지막 날만.
        // 올해를 고르면 오늘까지만 (미래 날짜는 목록에 없다)
        updateMonths();

        // 일: 고른 연·월에 있는 날만 (2월 29일은 윤년만)
        updateDays();

        AdapterView.OnItemSelectedListener dateChanged = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateMonths();
                updateDays();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spYear.setOnItemSelectedListener(dateChanged);
        spMonth.setOnItemSelectedListener(dateChanged);

        String[] genders = {"여성"};

        spGender.setAdapter(adapter(genders));

        emailInput = new EmailDomainInput(this,
                findViewById(R.id.etEmailId),
                findViewById(R.id.spEmailDomain),
                findViewById(R.id.etEmailDomain),
                null);
    }

    private ArrayAdapter<String> adapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                items
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        return adapter;
    }

    /** 맨 앞 항목("년도"·"월"·"일")은 칸에만 보이고 펼친 목록에서는 숨긴다 */
    private ArrayAdapter<String> placeholderAdapter(String[] items) {
        return EmailDomainInput.placeholderAdapter(this, items);
    }

    /** 연도를 아직 안 골랐으면 0 */
    private int selectedYear() {
        String year = String.valueOf(spYear.getSelectedItem());
        return YEAR_PLACEHOLDER.equals(year) ? 0 : Integer.parseInt(year.replace("년", ""));
    }

    /** 아직 안 골랐으면 0 (목록 0번이 "월"/"일") */
    private int selectedMonth() {
        return Math.max(spMonth.getSelectedItemPosition(), 0);
    }

    private int selectedDay() {
        return Math.max(spDay.getSelectedItemPosition(), 0);
    }

    /** 월 목록: 올해면 이번 달까지, 아니면 12월까지. 고른 달이 없어지면 마지막 달로 */
    private void updateMonths() {
        int monthCount = maxMonth(selectedYear(), Calendar.getInstance());

        if (spMonth.getAdapter() != null && spMonth.getAdapter().getCount() == monthCount + 1) {
            return;
        }
        int keep = Math.min(selectedMonth(), monthCount);

        String[] months = new String[monthCount + 1];
        months[0] = MONTH_PLACEHOLDER;
        for (int i = 1; i <= monthCount; i++) {
            months[i] = i + "월";
        }
        spMonth.setAdapter(placeholderAdapter(months));
        spMonth.setSelection(keep);
    }

    /** 일 목록을 고른 연·월의 날 수에 맞춘다 (올해 이번 달이면 오늘까지). 고른 날이 없어지면 마지막 날로 */
    private void updateDays() {
        // 연도 전이면 2월 29일까지, 월 전이면 31일까지 보여 준다
        int dayCount = maxDay(selectedYear(), selectedMonth(), Calendar.getInstance());

        if (spDay.getAdapter() != null && spDay.getAdapter().getCount() == dayCount + 1) {
            return;
        }
        int keep = Math.min(selectedDay(), dayCount);

        String[] days = new String[dayCount + 1];
        days[0] = DAY_PLACEHOLDER;
        for (int i = 1; i <= dayCount; i++) {
            days[i] = i + "일";
        }
        spDay.setAdapter(placeholderAdapter(days));
        spDay.setSelection(keep);
    }

    /** 고를 수 있는 마지막 달. 올해면 이번 달, 아니면(연도 전 포함) 12 */
    static int maxMonth(int year, Calendar today) {
        return year == today.get(Calendar.YEAR) ? today.get(Calendar.MONTH) + 1 : 12;
    }

    /** 고를 수 있는 마지막 날. 올해 이번 달이면 오늘, 월 전이면 31, 연도 전이면 윤년 기준 */
    static int maxDay(int year, int month, Calendar today) {
        if (month == 0) {
            return 31;
        }
        int last = daysInMonth(year == 0 ? 2000 : year, month);
        if (year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) + 1) {
            return Math.min(last, today.get(Calendar.DAY_OF_MONTH));
        }
        return last;
    }

    static int daysInMonth(int year, int month) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /** 중복확인을 마치면 버튼을 회색으로 막고, 아이디를 고치면 다시 검은색으로 연다 */
    private void setIdChecked(boolean checked) {
        isIdChecked = checked;
        btnCheckId.setEnabled(!checked);
        btnCheckId.setBackgroundResource(checked ? R.drawable.bg_button_gray : R.drawable.bg_button);
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

            String id = etId.getText().toString().trim();

            if (TextUtils.isEmpty(id)) {
                Toast.makeText(
                        this,
                        "아이디를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!LocalAccounts.isValidId(id)) {
                Toast.makeText(
                        this,
                        ID_RULE_MESSAGE,
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // [여기에 DB 입력] 같은 아이디로 가입한 회원이 있는지 확인 (users)
            if (LocalAccounts.idExists(this, id)) {
                Toast.makeText(
                        this,
                        "이미 사용 중인 아이디입니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            setIdChecked(true);

            Toast.makeText(
                    this,
                    "사용 가능한 아이디입니다.",
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
                if (isIdChecked) {
                    setIdChecked(false);
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

        // 약관 내용보기 (밑줄 회색 글씨)
        bindTerms(R.id.tvTerms1, "서비스 이용약관", R.raw.terms_service);
        bindTerms(R.id.tvTerms2, "개인정보 수집 및 이용 동의", R.raw.terms_privacy);
        bindTerms(R.id.tvTerms3, "만 14세 이상 확인", R.raw.terms_age);
        bindTerms(R.id.tvTerms4, "마케팅 정보 수신 동의", R.raw.terms_marketing);
        bindTerms(R.id.tvTerms5, "이벤트 및 프로모션 알림", R.raw.terms_event);

        // 가입하기
        btnNext.setOnClickListener(v -> {

            String id =
                    etId.getText().toString().trim();

            String pw =
                    etPw.getText().toString().trim();

            String pw2 =
                    etPw2.getText().toString().trim();

            String name =
                    etName.getText().toString().trim();

            int year = selectedYear();

            if (TextUtils.isEmpty(id)) {

                Toast.makeText(
                        this,
                        "아이디를 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!LocalAccounts.isValidId(id)) {

                Toast.makeText(
                        this,
                        ID_RULE_MESSAGE,
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!isIdChecked) {

                Toast.makeText(
                        this,
                        "아이디 중복확인을 해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!LocalAccounts.isValidPassword(pw)) {

                Toast.makeText(
                        this,
                        LocalAccounts.hasInvalidPasswordChar(pw)
                                ? PASSWORD_CHAR_MESSAGE
                                : PASSWORD_RULE_MESSAGE,
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!pw.equals(pw2)) {

                Toast.makeText(
                        this,
                        "비밀번호가 일치하지 않습니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (TextUtils.isEmpty(name)) {

                Toast.makeText(
                        this,
                        "이름을 입력해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // 이메일은 선택. 쓰다 만 주소나 이미 가입된 주소만 막는다
            String email = emailInput.email();

            if (email == null) {

                Toast.makeText(
                        this,
                        emailInput.isDomainMissing()
                                ? "이메일 주소(@ 뒤)를 선택해주세요."
                                : "이메일을 정확히 입력해주세요. 쓰지 않으려면 비워 두세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // [여기에 DB 입력] 이미 가입된 이메일인지 확인 (users)
            if (!email.isEmpty() && LocalAccounts.isTaken(this, "", email)) {

                Toast.makeText(
                        this,
                        "이미 가입된 이메일입니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // 생년월일은 선택. 셋 다 비우면 통과, 일부만 고르면 안내, 다 고르면 오늘 이후인지 본다
            int month = selectedMonth();
            int day = selectedDay();
            int chosen = (year != 0 ? 1 : 0) + (month != 0 ? 1 : 0) + (day != 0 ? 1 : 0);

            if (chosen != 0 && chosen != 3) {

                Toast.makeText(
                        this,
                        "생년월일을 모두 선택하거나 모두 비워 주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            Calendar birth = Calendar.getInstance();
            birth.clear();
            birth.set(year, month - 1, day);

            if (chosen == 3 && birth.after(Calendar.getInstance())) {

                Toast.makeText(
                        this,
                        "생년월일을 정확히 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!cb1.isChecked()
                    || !cb2.isChecked()
                    || !cb3.isChecked()) {

                Toast.makeText(
                        this,
                        "필수 약관에 모두 동의해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // 이메일을 썼으면 @ 뒤 도메인이 실제로 있는지 서버에 확인한다 (dsdsds.fdf 같은 주소 막기).
            // 확인이 끝나면 이 버튼을 다시 누른 것처럼 이어서 가입한다
            if (!email.isEmpty() && !email.equals(domainCheckedEmail)) {
                btnNext.setEnabled(false);
                RecommendApi.checkEmailDomain(email, new RecommendApi.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean ok) {
                        btnNext.setEnabled(true);
                        domainCheckedEmail = email;
                        btnNext.performClick();
                    }

                    @Override
                    public void onError(int code, String message) {
                        btnNext.setEnabled(true);
                        if (code == 0) {
                            // 서버에 연결이 안 되면 막지 않는다 (이메일은 선택 항목)
                            domainCheckedEmail = email;
                            btnNext.performClick();
                            return;
                        }
                        Toast.makeText(SigninActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }

            String gender =
                    spGender.getSelectedItem().toString();

            // [여기에 DB 입력] 회원가입 정보 저장 - 아이디, 비밀번호(암호화), 이름, 생년월일, 성별, 휴대폰, 이메일(선택) (users)
            //                 저장에 성공했을 때만 아래 완료 화면으로 이동할 것
            String phone = getIntent().getStringExtra(EXTRA_PHONE);

            boolean saved = LocalAccounts.register(
                    this,
                    id,
                    pw,
                    name,
                    phone,
                    email
            );

            if (!saved) {
                Toast.makeText(
                        this,
                        "이미 가입된 아이디, 휴대폰 번호 또는 이메일입니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // 가입하면 바로 로그인된 상태로 완료 화면에 간다
            LoginSession.login(this, id, name, email, phone);

            Intent intent =
                    new Intent(
                            SigninActivity.this,
                            SigninCompleteActivity.class
                    );

            intent.putExtra(
                    SigninCompleteActivity.EXTRA_NAME,
                    name
            );

            startActivity(intent);
            finish();
        });
    }

    private void bindTerms(int viewId, String title, int rawRes) {
        android.widget.TextView link = findViewById(viewId);
        link.setPaintFlags(link.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        link.setOnClickListener(v -> TermsActivity.open(this, title, rawRes));
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