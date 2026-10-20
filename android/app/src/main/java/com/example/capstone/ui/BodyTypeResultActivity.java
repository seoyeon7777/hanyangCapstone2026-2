
package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

import java.util.Locale;

public class BodyTypeResultActivity extends AppCompatActivity {

    private TextView resultTitle;
    private TextView resultDescription;
    private TextView resultScore;

    private float userHeightCm;

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

            Intent intent = new Intent(
                    BodyTypeResultActivity.this,
                    BodyMeasurementActivity.class
            );

            intent.putExtra(
                    "USER_HEIGHT_CM",
                    userHeightCm
            );

            startActivity(intent);
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
