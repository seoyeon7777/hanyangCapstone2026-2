
package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

public class BodyTypeResultActivity extends AppCompatActivity {

    private TextView resultTitle;
    private TextView resultDescription;
    private TextView resultFeatures;

    private float userHeightCm;

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

        resultDescription.setText(KoreanLineBreak.keepWords(summary));
        resultFeatures.setText(KoreanLineBreak.keepWords(features));
        KoreanLineBreak.apply(findViewById(R.id.resultNotice));

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
