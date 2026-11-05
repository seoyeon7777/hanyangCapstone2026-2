package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyPrior;
import com.example.capstone.data.BodyProfileStore;

/** 팀원 피팅 진입: 키·몸무게·나이 → 체형 자가진단. */
public class FittingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fitting);

        // 공통 하단 메뉴
        BottomMenu.setup(this, BottomMenu.TAB_FITTING);

        findViewById(R.id.btnStartFitting).setOnClickListener(v -> startFitting());
        findViewById(R.id.btnLoadSurvey).setOnClickListener(v -> loadLastSurvey());
        findViewById(R.id.btnShowFeedback).setOnClickListener(v -> showFeedback());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 설문을 마치고 돌아오면 최근 설문으로 갱신
        boolean hasSurvey = BodyTypeActivity.hasLastSurvey(this);
        findViewById(R.id.btnLoadSurvey).setAlpha(hasSurvey ? 1f : 0.5f);
        ((TextView) findViewById(R.id.tvLastSurvey)).setText(hasSurvey
                ? "최근 설문: " + BodyTypeActivity.lastSurveySummary(this)
                : "아직 저장된 설문이 없어요.");
    }

    private void showFeedback() {
        BodyProfileStore.Profile active = BodyProfileStore.getActive(this);

        // 대표 체형이 있으면(설문만 한 카드도) 그 카드로 보여 준다 → '내 체형 관리'와 같은 체형
        if (active != null) {
            startActivity(BodyFeedbackActivity.profileIntent(this, active));
        } else if (BodyTypeActivity.hasLastSurvey(this)) {
            startActivity(BodyFeedbackActivity.surveyIntent(this));
        } else {
            Toast.makeText(this, "아직 체형 분석 기록이 없어요. '체형 분석 시작하기'로 먼저 분석해 주세요.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startFitting() {
        float heightCm = readHeight();
        if (heightCm <= 0f) {
            return;
        }
        if (!readWeightAndAge(false)) {
            return;
        }

        Intent intent = new Intent(this, BodyTypeActivity.class);
        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
        startActivity(intent);
    }

    /** 최근 설문을 지난번 답이 선택된 채로 1번 문항부터 연다. */
    private void loadLastSurvey() {
        if (!BodyTypeActivity.hasLastSurvey(this)) {
            Toast.makeText(this, "아직 저장된 설문이 없어요. '체형 분석 시작하기'로 먼저 설문을 진행해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 키를 새로 입력했으면 그 키를, 비워두면 지난 설문 때의 키를 사용
        EditText editHeight = findViewById(R.id.editHeight);
        float savedHeight = BodyTypeActivity.lastSurveyHeight(this);
        boolean useSaved = editHeight.getText().toString().trim().isEmpty()
                && savedHeight >= 100f && savedHeight <= 230f;
        float heightCm = useSaved ? savedHeight : readHeight();
        if (heightCm <= 0f) {
            return;
        }
        // 몸무게·나이는 비워 두면 지난번에 입력한 값을 쓴다
        if (!readWeightAndAge(true)) {
            return;
        }

        startActivity(BodyTypeActivity.lastSurveyIntent(this, heightCm));
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
