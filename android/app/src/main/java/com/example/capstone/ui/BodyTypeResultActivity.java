
package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginGate;
import com.example.capstone.login.LoginSession;

public class BodyTypeResultActivity extends AppCompatActivity {

    private TextView resultTitle;
    private TextView resultDescription;
    private TextView resultFeatures;

    private float userHeightCm;

    /** 이 설문의 답과 결과 (비회원이 사진 측정 때문에 로그인하면 계정에 저장하려고 들고 있는다) */
    private int[] surveyAnswers;
    private int resultType;

    /** 이 결과가 지금 계정에 이미 저장됐는가 (로그인한 채 설문했으면 true) */
    private boolean savedToAccount;

    private static final String PREF_TIPS = "measure_tips";
    private static final String KEY_SKIP_CLOTHES_TIP = "skip_clothes_tip";

    private void startMeasurement() {

        Intent intent = new Intent(
                BodyTypeResultActivity.this,
                PhotoMeasurementActivity.class
        );

        intent.putExtra(
                "USER_HEIGHT_CM",
                userHeightCm
        );

        startActivity(intent);
    }

    /** 비회원이 사진 측정 때문에 로그인했을 때: 방금 한 설문 결과를 계정에 저장한다 (이 경로에서만 저장된다) */
    private void saveSurveyToAccountIfNeeded() {

        if (savedToAccount || surveyAnswers == null || !LoginSession.isLoggedIn(this)) {
            return;
        }

        BodyTypeActivity.persistSurvey(this, surveyAnswers, userHeightCm, resultType);
        BodyProfileStore.saveFromSurvey(this, userHeightCm,
                BodyTypeActivity.skeletonLabel(resultType), surveyAnswers);
        savedToAccount = true;
        findViewById(R.id.resultGuestNotice).setVisibility(View.GONE);
    }

    /** 사진을 고르기 전 옷차림 안내. 몸에 붙는 옷이 정확하다는 것을 알려 준다. */
    private void showClothesTip() {

        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView message = new TextView(this);
        message.setText(
                "• 몸에 붙는 옷 (레깅스·슬림한 티 등), 가능하면 어두운 단색\n"
                        + "• 어깨·팔꿈치·무릎이 보이는 옷 (롱코트, 긴 치마, 두꺼운 패딩은 피하기)\n"
                        + "• 팔은 몸에서 살짝 벌리고, 단색 배경 앞에서 전신이 나오게\n"
                        + "• 머리를 묶으면 머리 꼭대기가 더 정확히 잡혀요\n"
                        + "\n옷이 헐렁하면 가슴·허리·골반 둘레가 실제보다 크게 나와요."
        );
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.2f);
        box.addView(message);

        CheckBox skip = new CheckBox(this);
        skip.setText("다시 보지 않기");
        box.addView(skip);

        new AlertDialog.Builder(this)
                .setTitle("정확한 측정을 위해")
                .setView(box)
                .setPositiveButton("시작하기", (d, w) -> {
                    if (skip.isChecked()) {
                        getSharedPreferences(PREF_TIPS, MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_SKIP_CLOTHES_TIP, true)
                                .apply();
                    }
                    startMeasurement();
                })
                .setNegativeButton("다음에 하기", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_body_type_result);

        resultTitle = findViewById(R.id.resultTitle);
        resultDescription = findViewById(R.id.resultDescription);
        resultFeatures = findViewById(R.id.resultFeatures);

        Button btnYes = findViewById(R.id.btnYes);
        Button btnNo = findViewById(R.id.btnNo);
        View btnRetry = findViewById(R.id.btnRetry);

        // 설문 결과 가져오기
        Intent receivedIntent = getIntent();

        int resultType = receivedIntent.getIntExtra(
                BodyTypeActivity.EXTRA_RESULT_TYPE,
                BodyTypeActivity.TYPE_STRAIGHT
        );

        this.resultType = resultType;
        surveyAnswers = receivedIntent.getIntArrayExtra(BodyTypeActivity.EXTRA_ANSWERS);
        savedToAccount = LoginSession.isLoggedIn(this);

        // 비회원은 결과가 저장되지 않는다는 안내를 보여 준다 (로그인한 채 설문한 결과만 저장된다)
        findViewById(R.id.resultGuestNotice).setVisibility(savedToAccount ? View.GONE : View.VISIBLE);

        userHeightCm = receivedIntent.getFloatExtra(
                "USER_HEIGHT_CM",
                0f
        );

        // 키 전달이 누락된 경우 저장된 키 사용
        if (userHeightCm <= 0f) {
            userHeightCm = getSharedPreferences(
                    "avatar_input",
                    MODE_PRIVATE
            ).getFloat("height_cm", 0f);
        }

        // 체형별 결과 표시
        String typeName;
        String summary;
        String features;

        switch (resultType) {

            case BodyTypeActivity.TYPE_WAVE:

                typeName = "웨이브 체형";

                summary = "부드러운 곡선이 돋보이는 체형이에요.";

                features =
                        "• 부드럽고 곡선적인 신체 윤곽\n"
                                + "• 하체 중심의 비율\n"
                                + "• 허리선을 살린 스타일이 잘 어울려요";

                break;

            case BodyTypeActivity.TYPE_NATURAL:

                typeName = "내추럴 체형";

                summary = "뼈대와 관절의 존재감이 느껴지는 체형이에요.";

                features =
                        "• 뼈대와 관절의 존재감\n"
                                + "• 자연스러운 신체 윤곽\n"
                                + "• 여유 있는 실루엣이 잘 어울려요";

                break;

            case BodyTypeActivity.TYPE_STRAIGHT:
            default:

                typeName = "스트레이트 체형";

                summary = "입체감 있고 탄탄한 실루엣의 체형이에요.";

                features =
                        "• 상체의 입체감\n"
                                + "• 탄탄하고 곧은 신체 윤곽\n"
                                + "• 깔끔하고 정돈된 스타일이 잘 어울려요";

                break;
        }

        resultTitle.setText(typeName);

        // 서버 진단 비율 (예: 스트레이트 38% · 웨이브 41% · 내추럴 21%)
        float ratioS = receivedIntent.getFloatExtra(BodyTypeActivity.EXTRA_RATIO_S, -1f);

        if (ratioS >= 0f) {
            float ratioW = receivedIntent.getFloatExtra(BodyTypeActivity.EXTRA_RATIO_W, 0f);
            float ratioN = receivedIntent.getFloatExtra(BodyTypeActivity.EXTRA_RATIO_N, 0f);

            summary += "\n\n스트레이트 " + Math.round(ratioS * 100) + "%"
                    + " · 웨이브 " + Math.round(ratioW * 100) + "%"
                    + " · 내추럴 " + Math.round(ratioN * 100) + "%";

            if (receivedIntent.getBooleanExtra(BodyTypeActivity.EXTRA_MIXED, false)) {
                summary += "\n두 체형의 특징이 함께 보여요. 추천도 두 체형을 섞어서 골라 드려요.";
            }
        }

        resultDescription.setText(KoreanLineBreak.keepWords(summary));
        resultFeatures.setText(KoreanLineBreak.keepWords(features));
        KoreanLineBreak.apply(findViewById(R.id.resultNotice));

        // 자세한 신체 분석 → 정면·옆면 사진 고르기. 로그인해야 할 수 있다 (비회원이면 로그인 안내 → 로그인 뒤 이어서 시작)
        btnYes.setOnClickListener(v -> LoginGate.require(this,
                "사진으로 치수를 측정하려면 로그인해야 해요.\n로그인하면 방금 한 설문 결과도 내 체형으로 저장돼요.",
                () -> {

            if (userHeightCm < 100f || userHeightCm > 230f) {
                Toast.makeText(
                        this,
                        "키 정보가 없습니다. 피팅 시작 화면에서 키를 다시 입력해주세요.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // 비회원이 여기서 로그인하고 이어가는 경우: 방금 한 설문 결과를 지금 계정의 내 체형으로 저장한다
            saveSurveyToAccountIfNeeded();

            // 사진을 고르기 전에 옷차림 안내 (다시 보지 않기를 고르면 건너뜀)
            if (getSharedPreferences(PREF_TIPS, MODE_PRIVATE)
                    .getBoolean(KEY_SKIP_CLOTHES_TIP, false)) {
                startMeasurement();
            } else {
                showClothesTip();
            }
        }));

        // 아니오: 결과 화면 종료
        btnNo.setOnClickListener(v -> finish());

        // 다시 검사하기
        btnRetry.setOnClickListener(v -> {

            Intent intent = new Intent(
                    BodyTypeResultActivity.this,
                    BodyTypeActivity.class
            );

            intent.putExtra(
                    "USER_HEIGHT_CM",
                    userHeightCm
            );

            startActivity(intent);
            finish();
        });
    }
}
