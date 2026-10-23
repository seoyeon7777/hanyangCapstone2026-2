package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
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

public class SigninActivity extends AppCompatActivity {

    private ImageButton btnBack, btnHome;

    private EditText etId, etPw, etPw2, etName, etYear;
    private Button btnCheckId, btnNext;
    private ImageButton btnTogglePw1, btnTogglePw2;

    private Spinner spMonth, spDay, spGender;

    private CheckBox cbAll, cb1, cb2, cb3, cb4, cb5;

    private boolean isIdChecked = false;
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
        etYear = findViewById(R.id.etYear);

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


        String[] days = new String[31];

        for (int i = 0; i < 31; i++) {
            days[i] = (i + 1) + "일";
        }

        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                days
        );

        dayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spDay.setAdapter(dayAdapter);


        String[] genders = {"남성", "여성"};

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                genders
        );

        genderAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spGender.setAdapter(genderAdapter);
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

            if (id.length() < 8) {
                Toast.makeText(
                        this,
                        "아이디는 8자 이상이어야 합니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // [여기에 DB 입력] 같은 아이디로 가입한 회원이 있는지 확인 (users)
            //                 있으면 "이미 사용 중인 아이디입니다." 안내 후 return
            isIdChecked = true;

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
                isIdChecked = false;
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
        btnNext.setOnClickListener(v -> {

            String id =
                    etId.getText().toString().trim();

            String pw =
                    etPw.getText().toString().trim();

            String pw2 =
                    etPw2.getText().toString().trim();

            String name =
                    etName.getText().toString().trim();

            String year =
                    etYear.getText().toString().trim();

            if (TextUtils.isEmpty(id)) {

                Toast.makeText(
                        this,
                        "아이디를 입력해주세요.",
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

            if (TextUtils.isEmpty(pw)
                    || pw.length() < 8) {

                Toast.makeText(
                        this,
                        "비밀번호는 8자 이상 입력해주세요.",
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

            if (TextUtils.isEmpty(year)
                    || year.length() != 4) {

                Toast.makeText(
                        this,
                        "출생년도 4자리를 입력해주세요.",
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

            String month =
                    spMonth.getSelectedItem().toString();

            String day =
                    spDay.getSelectedItem().toString();

            String gender =
                    spGender.getSelectedItem().toString();

            // [여기에 DB 입력] 회원가입 정보 저장 - 아이디, 비밀번호(암호화), 이름, 생년월일, 성별, 휴대폰 (users)
            //                 저장에 성공했을 때만 아래 완료 화면으로 이동할 것
            Toast.makeText(
                    this,
                    name + "님, 회원가입 완료! (" +
                            year + "년 " +
                            month + " " +
                            day + ", " +
                            gender + ")",
                    Toast.LENGTH_LONG
            ).show();

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