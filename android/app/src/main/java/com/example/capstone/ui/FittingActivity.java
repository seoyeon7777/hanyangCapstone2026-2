package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyPrior;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginGate;
import com.example.capstone.login.LoginSession;

/** 팀원 피팅 진입: 키·몸무게·나이 → 체형 자가진단. */
public class FittingActivity extends AppCompatActivity {

    /** true면 체형분석 탭의 첫 화면(저장한 체형이 없을 때)으로 연 것 → 뒤로 버튼을 숨기고 제목 "체형 분석" */
    public static final String EXTRA_AS_TAB = "fitting_as_tab";

    private static final String PREF_TIPS = "survey_tips";
    private static final String KEY_SKIP_GUEST_NOTICE = "skip_guest_notice";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fitting);

        // 공통 하단 메뉴
        BottomMenu.setup(this, BottomMenu.TAB_FITTING);

        findViewById(R.id.btnStartFitting).setOnClickListener(v -> startFitting());
        // 체형분석 탭(내 체형관리)으로 돌아가기
        findViewById(R.id.btnFittingBack).setOnClickListener(v -> finish());
        applyTabMode(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyTabMode(intent);
    }

    private void applyTabMode(Intent intent) {
        boolean asTab = intent.getBooleanExtra(EXTRA_AS_TAB, false);
        findViewById(R.id.btnFittingBack).setVisibility(asTab ? View.INVISIBLE : View.VISIBLE);
        ((TextView) findViewById(R.id.tvFittingTitle)).setText(asTab ? "체형 분석" : "새 체형 추가");
    }

    private void startFitting() {
        float heightCm = readHeight();
        if (heightCm <= 0f) {
            return;
        }
        if (!readWeightAndAge(false)) {
            return;
        }

        // 비회원이면 설문 전에 "결과는 앱을 실행하는 동안에만 저장돼요" 안내를 먼저 보여 준다
        if (!LoginSession.isLoggedIn(this)
                && !getSharedPreferences(PREF_TIPS, MODE_PRIVATE).getBoolean(KEY_SKIP_GUEST_NOTICE, false)) {
            showGuestNotice(heightCm);
            return;
        }

        beginSurvey(heightCm);
    }

    private void beginSurvey(float heightCm) {
        Intent intent = new Intent(this, BodyTypeActivity.class);
        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
        startActivity(intent);
    }

    /**
     * 비회원 안내 창: 로그인하기(로그인하면 설문 시작으로 이어짐) / 그대로 진행.
     * "다시 보지 않기"를 체크하면 다음부터 이 창을 건너뛴다 (결과 화면에는 같은 안내가 계속 보인다).
     */
    private void showGuestNotice(float heightCm) {

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_guest_survey_notice, null, false);
        CheckBox skip = view.findViewById(R.id.cbSkipGuestNotice);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.btnGuestLogin).setOnClickListener(v -> {
            rememberSkip(skip.isChecked());
            dialog.dismiss();
            LoginGate.startLogin(this, () -> beginSurvey(heightCm));
        });
        view.findViewById(R.id.btnGuestProceed).setOnClickListener(v -> {
            rememberSkip(skip.isChecked());
            dialog.dismiss();
            beginSurvey(heightCm);
        });

        dialog.show();
    }

    private void rememberSkip(boolean skip) {
        if (skip) {
            getSharedPreferences(PREF_TIPS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SKIP_GUEST_NOTICE, true)
                    .apply();
        }
    }

    /**
     * 몸무게(kg)·나이를 확인해 저장한다. 잘못되면 안내 후 false.
     * useSavedWhenBlank가 true면 비어 있을 때 지난번 값을 쓴다.
     */
    private boolean readWeightAndAge(boolean useSavedWhenBlank) {
        EditText editWeight = findViewById(R.id.editWeight);
        EditText editAge = findViewById(R.id.editAge);
        String weightText = editWeight.getText().toString().trim();
        String ageText = editAge.getText().toString().trim();

        float weightKg;
        if (weightText.isEmpty()) {
            weightKg = useSavedWhenBlank ? BodyInput.weight(this) : -1f;
            if (weightKg <= 0f) {
                Toast.makeText(this, "몸무게를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else {
            try {
                weightKg = Float.parseFloat(weightText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "몸무게를 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (weightKg < 25f || weightKg > 150f) {
                Toast.makeText(this, "몸무게를 25~150kg 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        float age;
        if (ageText.isEmpty()) {
            age = useSavedWhenBlank ? BodyInput.age(this) : -1f;
            if (age <= 0f) {
                Toast.makeText(this, "나이를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else {
            try {
                age = Integer.parseInt(ageText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "나이를 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (age < BodyPrior.INPUT_AGE_MIN || age > BodyPrior.INPUT_AGE_MAX) {
                Toast.makeText(this, "나이를 10~99세 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        BodyInput.save(this, -1f, weightKg, age);
        // 입력한 값으로 사이즈 알림용 치수를 바로 갱신한다
        BodyProfileStore.syncActiveProfile(this);
        return true;
    }

    /** 입력한 키를 확인해 저장하고 돌려준다. 잘못된 값이면 안내 후 -1. */
    private float readHeight() {
        EditText editHeight = findViewById(R.id.editHeight);
        String heightText = editHeight.getText().toString().trim();
        if (heightText.isEmpty()) {
            Toast.makeText(this, "키를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }

        float heightCm;
        try {
            heightCm = Float.parseFloat(heightText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "키를 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }
        if (heightCm < 100f || heightCm > 230f) {
            Toast.makeText(this, "키를 100~230cm 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return -1f;
        }

        getSharedPreferences("avatar_input", MODE_PRIVATE)
                .edit()
                .putFloat("height_cm", heightCm)
                .apply();
        return heightCm;
    }
}
