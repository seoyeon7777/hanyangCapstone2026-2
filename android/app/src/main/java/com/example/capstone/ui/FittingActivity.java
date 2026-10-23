package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.BuildConfig;
import com.example.capstone.R;
import com.example.capstone.data.BodyMeasurementContract;

/** 팀원 피팅 진입: 키 → 체형 자가진단. */
public class FittingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fitting);

        // 공통 하단 메뉴
        BottomMenu.setup(this, BottomMenu.TAB_FITTING);

        findViewById(R.id.btnStartFitting).setOnClickListener(v -> startFitting());
        findViewById(R.id.btnLoadSurvey).setOnClickListener(v -> loadLastSurvey());

        View btnDemo = findViewById(R.id.btnDemoSamples);
        if (BuildConfig.DEBUG && btnDemo != null) {
            btnDemo.setVisibility(View.VISIBLE);
            btnDemo.setOnClickListener(v ->
                    startActivity(new Intent(this, DemoSampleActivity.class)));
        }
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

    private void startFitting() {
        float heightCm = readHeight();
        if (heightCm <= 0f) {
            return;
        }

        Intent intent = new Intent(this, BodyTypeActivity.class);
        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
        startActivity(intent);
    }

    /** 최근 설문을 지난번 답이 선택된 채로 1번 문항부터 연다. */
    private void loadLastSurvey() {
        if (!BodyTypeActivity.hasLastSurvey(this)) {
            Toast.makeText(this, "아직 저장된 설문이 없어요. '가상 피팅 시작하기'로 먼저 설문을 진행해주세요.",
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

        startActivity(BodyTypeActivity.lastSurveyIntent(this, heightCm));
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
