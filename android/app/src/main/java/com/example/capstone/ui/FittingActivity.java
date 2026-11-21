package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.login.LoginGate;
import com.example.capstone.login.LoginSession;

/** 체형 분석 진입: 설문 시작 (키·몸무게·나이는 사진으로 치수를 잴 때 받는다). */
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
    protected void onResume() {
        super.onResume();
        // 체형 분석은 로그인해야 쓸 수 있다 (찜 화면과 같음). 상품 상세 "측정하러 가기" 등 다른 길로 와도 막는다
        LoginRequiredView.update(this, R.drawable.ic_nav_fitting,
                "체형 분석은 로그인 후 이용할 수 있습니다.", findViewById(R.id.scrollFitting));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyTabMode(intent);
    }

    private void applyTabMode(Intent intent) {
        boolean asTab = intent.getBooleanExtra(EXTRA_AS_TAB, false);
        ((TextView) findViewById(R.id.tvFittingTitle)).setText(asTab ? "체형 분석" : "새 체형 추가");
    }

    private void startFitting() {
        // 설문(골격 체형 진단)에는 키·몸무게·나이를 쓰지 않는다. 그 값은 사진으로 치수를 잴 때 받는다
        // (BodyInputDialog). 지난번에 넣은 키가 있으면 설문 카드에 같이 저장하도록 넘긴다.
        float heightCm = Math.max(0f, BodyInput.height(this));

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
}
