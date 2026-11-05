package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

    /** true면 체형분석 탭의 첫 화면(저장한 체형이 없을 때)으로 연 것 → 뒤로 버튼을 숨기고 제목 "체형 분석" */
    public static final String EXTRA_AS_TAB = "fitting_as_tab";

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

        Intent intent = new Intent(this, BodyTypeActivity.class);
        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
        startActivity(intent);
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
