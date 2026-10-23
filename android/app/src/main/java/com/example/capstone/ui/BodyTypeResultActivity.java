
package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

import java.util.Locale;

public class BodyTypeResultActivity extends AppCompatActivity {

    private TextView resultTitle;
    private TextView resultDescription;
    private TextView resultScore;

    private float userHeightCm;

    private static final String PREF_TIPS = "measure_tips";
    private static final String KEY_SKIP_CLOTHES_TIP = "skip_clothes_tip";

    private void startMeasurement() {

        Intent intent = new Intent(
                BodyTypeResultActivity.this,
                BodyMeasurementActivity.class
        );

        intent.putExtra(
                "USER_HEIGHT_CM",
                userHeightCm
        );

        startActivity(intent);
    }

    /** 촬영 전 옷차림 안내. 몸에 붙는 옷이 정확하다는 것을 알려 준다. */
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
        resultScore = findViewById(R.id.resultScore);

        Button btnYes = findViewById(R.id.btnYes);
        Button btnNo = findViewById(R.id.btnNo);
        Button btnRetry = findViewById(R.id.btnRetry);

        // 설문 결과 가져오기
        Intent receivedIntent = getIntent();

        int resultType = receivedIntent.getIntExtra(
                BodyTypeActivity.EXTRA_RESULT_TYPE,
                BodyTypeActivity.TYPE_STRAIGHT
        );

        int[] scores = receivedIntent.getIntArrayExtra(
                BodyTypeActivity.EXTRA_SCORES
        );

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

        if (scores == null || scores.length < 3) {
            scores = new int[]{0, 0, 0};
        }

        // 체형별 결과 표시
        String typeName;
        String description;

        switch (resultType) {

            case BodyTypeActivity.TYPE_WAVE:

                typeName = "웨이브 체형";

                description =
                        "자가진단 결과, 웨이브 체형의 특징과 "
                                + "가장 가까운 것으로 나타났어요.\n\n"
                                + "웨이브 체형은 일반적으로 부드러운 "
                                + "신체 윤곽과 하체 중심의 비율 등이 "
                                + "특징으로 설명됩니다.\n\n"
                                + "다만 이는 설문 응답을 기반으로 한 "
                                + "참고용 분류이며, 실제 신체 치수나 "
                                + "체형을 확정하는 결과는 아닙니다.";

                break;

            case BodyTypeActivity.TYPE_NATURAL:

                typeName = "내추럴 체형";

                description =
                        "자가진단 결과, 내추럴 체형의 특징과 "
                                + "가장 가까운 것으로 나타났어요.\n\n"
                                + "내추럴 체형은 일반적으로 뼈대와 "
                                + "관절의 존재감, 비교적 자연스러운 "
                                + "신체 윤곽 등이 특징으로 설명됩니다.\n\n"
                                + "다만 이는 설문 응답을 기반으로 한 "
                                + "참고용 분류이며, 실제 신체 치수나 "
                                + "체형을 확정하는 결과는 아닙니다.";

                break;

            case BodyTypeActivity.TYPE_STRAIGHT:
            default:

                typeName = "스트레이트 체형";

                description =
                        "자가진단 결과, 스트레이트 체형의 "
                                + "특징과 가장 가까운 것으로 나타났어요.\n\n"
                                + "스트레이트 체형은 일반적으로 "
                                + "상체의 입체감과 비교적 탄탄한 "
                                + "신체 윤곽 등이 특징으로 설명됩니다.\n\n"
                                + "다만 이는 설문 응답을 기반으로 한 "
                                + "참고용 분류이며, 실제 신체 치수나 "
                                + "체형을 확정하는 결과는 아닙니다.";

                break;
        }

        resultTitle.setText(typeName);
        resultDescription.setText(description);

        // 설문 점수 표시
        resultScore.setText(
                String.format(
                        Locale.KOREA,
                        "스트레이트  %d점\n"
                                + "웨이브  %d점\n"
                                + "내추럴  %d점",
                        scores[0],
                        scores[1],
                        scores[2]
                )
        );

        // 자세한 신체 분석 → 카메라 실행
        btnYes.setOnClickListener(v -> {

            if (userHeightCm < 100f || userHeightCm > 230f) {
                Toast.makeText(
                        this,
                        "키 정보가 없습니다. 피팅 시작 화면에서 키를 다시 입력해주세요.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // 카메라를 켜기 전에 옷차림 안내 (다시 보지 않기를 고르면 건너뜀)
            if (getSharedPreferences(PREF_TIPS, MODE_PRIVATE)
                    .getBoolean(KEY_SKIP_CLOTHES_TIP, false)) {
                startMeasurement();
            } else {
                showClothesTip();
            }
        });

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
