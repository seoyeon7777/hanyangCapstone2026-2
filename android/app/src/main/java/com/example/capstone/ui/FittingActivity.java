package com.example.capstone.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.mypage.MyBodyActivity;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyPrior;
import com.example.capstone.data.BodyProfileStore;

import java.util.List;

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
        findViewById(R.id.btnManageBodies).setOnClickListener(v -> openBodyManager());
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
        showMyBodies();
    }

    // =====================================
    // 내 체형 (예전 마이페이지 '내 체형관리') — 저장한 체형 목록, 누르면 현재 체형으로 선택
    // 이름 변경·삭제·설정은 "관리 ›" → MyBodyActivity
    // =====================================

    private void showMyBodies() {
        LinearLayout list = findViewById(R.id.layoutMyBodies);
        TextView title = findViewById(R.id.tvMyBodyTitle);
        list.removeAllViews();

        if (!LoginSession.isLoggedIn(this)) {
            title.setText("내 체형");
            list.addView(messageRow("로그인하면 체형을 여러 개 저장하고 골라 쓸 수 있어요. 눌러서 로그인 ›",
                    v -> startActivity(new Intent(this, LoginMainActivity.class))));
            return;
        }

        List<BodyProfileStore.Profile> profiles = BodyProfileStore.getProfiles(this);
        String activeId = BodyProfileStore.getActiveId(this);
        title.setText("내 체형 " + profiles.size() + "개");

        if (profiles.isEmpty()) {
            list.addView(messageRow("아직 저장한 체형이 없어요. '체형 분석 시작하기'로 분석한 뒤 저장해 주세요.", null));
            return;
        }

        for (BodyProfileStore.Profile profile : profiles) {
            list.addView(bodyRow(profile, profile.id.equals(activeId)));
        }
    }

    private View bodyRow(BodyProfileStore.Profile profile, boolean current) {
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * density);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        row.setBackgroundResource(current ? R.drawable.bg_card_active : R.drawable.bg_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(8 * density);
        row.setLayoutParams(params);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(profile.name);
        name.setTextColor(0xFF111111);
        name.setTextSize(15f);
        name.setTypeface(null, Typeface.BOLD);
        texts.addView(name);

        TextView summary = new TextView(this);
        summary.setText(profile.bodyType
                + (profile.isSurveyOnly() ? " · 설문" : " · 사진 측정")
                + " · " + profile.savedDate());
        summary.setTextColor(0xFF777777);
        summary.setTextSize(12f);
        texts.addView(summary);

        TextView state = new TextView(this);
        state.setTextSize(12f);
        state.setPadding(Math.round(8 * density), Math.round(3 * density),
                Math.round(8 * density), Math.round(3 * density));
        if (current) {
            state.setText("현재 체형");
            state.setTextColor(0xFFFFFFFF);
            state.setBackgroundResource(R.drawable.bg_badge_black);
        } else {
            state.setText("선택");
            state.setTextColor(0xFF555555);
        }
        row.addView(state);

        if (!current) {
            row.setOnClickListener(v -> {
                BodyProfileStore.setActive(this, profile.id);
                Toast.makeText(this, "'" + profile.name + "'을(를) 현재 체형으로 선택했어요. 홈 추천도 이 체형으로 바뀌어요.",
                        Toast.LENGTH_SHORT).show();
                showMyBodies();
            });
        }
        return row;
    }

    private View messageRow(String text, View.OnClickListener onClick) {
        float density = getResources().getDisplayMetrics().density;
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(0xFF777777);
        message.setTextSize(13f);
        int pad = Math.round(14 * density);
        message.setPadding(pad, pad, pad, pad);
        message.setBackgroundResource(R.drawable.bg_card);
        if (onClick != null) {
            message.setOnClickListener(onClick);
        }
        return message;
    }

    private void openBodyManager() {
        if (!LoginSession.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginMainActivity.class));
            return;
        }
        startActivity(new Intent(this, MyBodyActivity.class));
    }

    private void showFeedback() {
        BodyProfileStore.Profile active = BodyProfileStore.getActive(this);

        // 대표 체형이 있으면(설문만 한 카드도) 그 카드로 보여 준다 → '내 체형 관리'와 같은 체형
        if (active != null) {
            startActivity(BodyFeedbackActivity.profileIntent(this, active));
        } else if (BodyTypeActivity.hasLastSurvey(this) && !LoginSession.isLoggedIn(this)) {
            // 비로그인은 체형을 저장할 수 없어 최근 설문으로 보여 준다. 로그인했으면 저장한 체형만 (홈 추천과 같게)
            startActivity(BodyFeedbackActivity.surveyIntent(this));
        } else {
            Toast.makeText(this, "아직 저장된 체형이 없어요. '체형 분석 시작하기'로 분석한 뒤 저장해 주세요.",
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
