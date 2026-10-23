
package com.example.capstone.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.UserBodyProfile;

import java.io.File;
import java.util.Locale;

public class MeasurementResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_measurement_result);

        // =========================================
        // 기본 신체 치수
        // =========================================

        TextView textHeight =
                findViewById(R.id.textResultHeight);

        TextView textShoulder =
                findViewById(R.id.textResultShoulder);

        TextView textArm =
                findViewById(R.id.textResultArm);

        TextView textTorso =
                findViewById(R.id.textResultTorso);

        TextView textLeg =
                findViewById(R.id.textResultLeg);

        // =========================================
        // 신체 둘레
        // =========================================

        TextView textChest =
                findViewById(R.id.textResultChest);

        TextView textWaist =
                findViewById(R.id.textResultWaist);

        TextView textHip =
                findViewById(R.id.textResultHip);

        TextView textNeck =
                findViewById(R.id.textResultNeck);

        TextView textUpperArm =
                findViewById(R.id.textResultUpperArm);

        TextView textForearm =
                findViewById(R.id.textResultForearm);

        TextView textThigh =
                findViewById(R.id.textResultThigh);

        TextView textCalf =
                findViewById(R.id.textResultCalf);

        // =========================================
        // 촬영 사진
        // =========================================

        ImageView imageFront =
                findViewById(R.id.imageResultFront);

        ImageView imageSide =
                findViewById(R.id.imageResultSide);

        // =========================================
        // 비율 분석
        // =========================================

        TextView textUpperRatio =
                findViewById(R.id.textUpperRatio);

        TextView textLowerRatio =
                findViewById(R.id.textLowerRatio);

        TextView textWhr =
                findViewById(R.id.textWhr);

        // =========================================
        // 기본 측정값 가져오기
        // =========================================

        Intent receivedIntent = getIntent();

        float height =
                receivedIntent.getFloatExtra(
                        "HEIGHT", 0f
                );

        float shoulder =
                receivedIntent.getFloatExtra(
                        "SHOULDER", 0f
                );

        float torso =
                receivedIntent.getFloatExtra(
                        "TORSO", 0f
                );

        float armLength =
                receivedIntent.getFloatExtra(
                        "ARM_LENGTH", -1f
                );

        float legLength =
                receivedIntent.getFloatExtra(
                        "LEG_LENGTH", -1f
                );

        // =========================================
        // 정면 카메라 측정 방식 호환
        // =========================================

        if (armLength < 0f) {

            float leftArm =
                    receivedIntent.getFloatExtra(
                            "LEFT_ARM", -1f
                    );

            float rightArm =
                    receivedIntent.getFloatExtra(
                            "RIGHT_ARM", -1f
                    );

            armLength = averageValid(
                    leftArm,
                    rightArm
            );
        }

        if (legLength < 0f) {

            float leftLeg =
                    receivedIntent.getFloatExtra(
                            "LEFT_LEG", -1f
                    );

            float rightLeg =
                    receivedIntent.getFloatExtra(
                            "RIGHT_LEG", -1f
                    );

            legLength = averageValid(
                    leftLeg,
                    rightLeg
            );
        }

        // =========================================
        // 정면 + 측면으로 계산한 둘레
        // =========================================

        float chest =
                receivedIntent.getFloatExtra(
                        "CHEST_CIRCUMFERENCE", -1f
                );

        float waist =
                receivedIntent.getFloatExtra(
                        "WAIST_CIRCUMFERENCE", -1f
                );

        float hip =
                receivedIntent.getFloatExtra(
                        "HIP_CIRCUMFERENCE", -1f
                );

        // 아직 계산되지 않은 부위는
        // 측정값 없음으로 표시한다.

        float neck =
                receivedIntent.getFloatExtra(
                        "NECK_CIRCUMFERENCE", -1f
                );

        float upperArm =
                receivedIntent.getFloatExtra(
                        "UPPER_ARM_CIRCUMFERENCE", -1f
                );

        float forearm =
                receivedIntent.getFloatExtra(
                        "FOREARM_CIRCUMFERENCE", -1f
                );

        float thigh =
                receivedIntent.getFloatExtra(
                        "THIGH_CIRCUMFERENCE", -1f
                );

        float calf =
                receivedIntent.getFloatExtra(
                        "CALF_CIRCUMFERENCE", -1f
                );

        // =========================================
        // 앱 전체에서 사용하는 신체 정보 저장
        // 쇼핑 화면 사이즈 주의 기능 유지
        // =========================================

        UserBodyProfile.getInstance()
                .updateFromMeasurement(
                        height,
                        shoulder,
                        chest,
                        waist,
                        hip,
                        thigh
                );

        // =========================================
        // 기본 신체 정보 출력
        // =========================================

        textHeight.setText(
                formatBasic("키", height)
        );

        textShoulder.setText(
                formatBasic("어깨너비", shoulder)
        );

        textArm.setText(
                formatBasic("팔 길이", armLength)
        );

        textTorso.setText(
                formatBasic("상체 길이", torso)
        );

        textLeg.setText(
                formatBasic("다리 길이", legLength)
        );

        // =========================================
        // 신체 둘레 출력
        // =========================================

        textChest.setText(
                formatCard("가슴둘레", chest)
        );

        textWaist.setText(
                formatCard("허리둘레", waist)
        );

        textHip.setText(
                formatCard("골반둘레", hip)
        );

        textNeck.setText(
                formatCard("목둘레", neck)
        );

        textUpperArm.setText(
                formatCard("상완둘레", upperArm)
        );

        textForearm.setText(
                formatCard("전완둘레", forearm)
        );

        textThigh.setText(
                formatCard("허벅지둘레", thigh)
        );

        textCalf.setText(
                formatCard("종아리둘레", calf)
        );

        // =========================================
        // 정면 사진 표시
        // 기존 URI 방식 + 새 카메라 PATH 방식
        // 둘 다 지원
        // =========================================

        String frontUriString =
                receivedIntent.getStringExtra(
                        "FRONT_PHOTO_URI"
                );

        String frontPhotoPath =
                receivedIntent.getStringExtra(
                        "FRONT_PHOTO_PATH"
                );

        displayPhoto(
                imageFront,
                frontUriString,
                frontPhotoPath
        );

        // =========================================
        // 측면 사진 표시
        // =========================================

        String sideUriString =
                receivedIntent.getStringExtra(
                        "SIDE_PHOTO_URI"
                );

        String sidePhotoPath =
                receivedIntent.getStringExtra(
                        "SIDE_PHOTO_PATH"
                );

        displayPhoto(
                imageSide,
                sideUriString,
                sidePhotoPath
        );

        // =========================================
        // 상체 비율
        // =========================================

        if (height > 0f && torso > 0f) {

            float upperRatio =
                    (torso / height) * 100f;

            textUpperRatio.setText(
                    String.format(
                            Locale.KOREA,
                            "상체 비율\n\n%.1f%%",
                            upperRatio
                    )
            );

        } else {

            textUpperRatio.setText(
                    "상체 비율\n\n측정값 없음"
            );
        }

        // =========================================
        // 하체 비율
        // =========================================

        if (height > 0f && legLength > 0f) {

            float lowerRatio =
                    (legLength / height) * 100f;

            textLowerRatio.setText(
                    String.format(
                            Locale.KOREA,
                            "하체 비율\n\n%.1f%%",
                            lowerRatio
                    )
            );

        } else {

            textLowerRatio.setText(
                    "하체 비율\n\n측정값 없음"
            );
        }

        // =========================================
        // 허리-골반 비율 (WHR)
        // =========================================

        if (waist > 0f && hip > 0f) {

            float whr = waist / hip;

            textWhr.setText(
                    String.format(
                            Locale.KOREA,
                            "허리-골반 비율\n(WHR)\n\n%.2f",
                            whr
                    )
            );

        } else {

            textWhr.setText(
                    "허리-골반 비율\n(WHR)\n\n-"
            );
        }

        // =========================================
        // 확인 버튼 → 3D 아바타 미리보기
        // 기존 기능 유지
        // =========================================

        final float outHeight = height;
        final float outShoulder = shoulder;
        final float outArm = armLength;
        final float outChest = chest;
        final float outWaist = waist;
        final float outHip = hip;
        final float outUpperArm = upperArm;
        final float outForearm = forearm;
        final float outThigh = thigh;
        final float outCalf = calf;
        final float outNeck = neck;
        final float outTorso = torso;
        final float outLeg = legLength;

        findViewById(R.id.btnMeasurementDone)
                .setOnClickListener(v -> {

                    Intent intent =
                            BodyMeasurementContract
                                    .editIntent(
                                            this,
                                            outHeight,
                                            positiveOrNull(outShoulder),
                                            positiveOrNull(outArm),
                                            positiveOrNull(outChest),
                                            positiveOrNull(outWaist),
                                            positiveOrNull(outHip),
                                            positiveOrNull(outUpperArm),
                                            positiveOrNull(outForearm),
                                            positiveOrNull(outThigh),
                                            positiveOrNull(outCalf),
                                            positiveOrNull(outNeck),
                                            positiveOrNull(outTorso),
                                            positiveOrNull(outLeg)
                                    );

                    String sampleId =
                            getIntent().getStringExtra(
                                    BodyEditActivity.EXTRA_SAMPLE_ID
                            );

                    String sampleLabel =
                            getIntent().getStringExtra(
                                    BodyEditActivity.EXTRA_SAMPLE_LABEL
                            );

                    if (sampleId != null &&
                            !sampleId.isEmpty()) {

                        intent.putExtra(
                                BodyEditActivity.EXTRA_SAMPLE_ID,
                                sampleId
                        );
                    }

                    // 골격 진단 보조 값 (가슴두께 cm, 어깨 경사각 도). 저장하면 추천 서버로 간다
                    intent.putExtra(BodyEditActivity.EXTRA_CHEST_DEPTH_CM,
                            getIntent().getFloatExtra("CHEST_DEPTH", -1f));
                    intent.putExtra(BodyEditActivity.EXTRA_SHOULDER_SLOPE_DEG,
                            getIntent().getFloatExtra("SHOULDER_SLOPE_DEG", -1f));

                    intent.putExtra(
                            BodyEditActivity.EXTRA_SAMPLE_LABEL,
                            sampleLabel != null &&
                                    !sampleLabel.isEmpty()
                                    ? sampleLabel
                                    : "카메라/사진 측정"
                    );

                    startActivity(intent);
                });
    }

    // =========================================
    // 사진 표시
    // 기존 URI / 카메라 파일 경로 모두 지원
    // =========================================

    private void displayPhoto(
            ImageView imageView,
            String uriString,
            String photoPath
    ) {

        if (uriString != null &&
                !uriString.isEmpty()) {

            try {

                imageView.setImageURI(
                        Uri.parse(uriString)
                );

                if (imageView.getDrawable() != null) {
                    return;
                }

            } catch (Exception ignored) {
                // PATH 방식으로 재시도
            }
        }

        if (photoPath != null &&
                !photoPath.isEmpty()) {

            try {

                File photoFile =
                        new File(photoPath);

                if (photoFile.exists()) {

                    imageView.setImageURI(
                            Uri.fromFile(photoFile)
                    );

                    return;
                }

            } catch (Exception ignored) {
                // 사진을 표시하지 못한 경우
            }
        }

        imageView.setImageDrawable(null);
    }

    // =========================================
    // 좌우 측정값 평균
    // =========================================

    private static float averageValid(
            float left,
            float right
    ) {

        boolean hasLeft = left > 0f;
        boolean hasRight = right > 0f;

        if (hasLeft && hasRight) {
            return (left + right) / 2f;
        }

        if (hasLeft) {
            return left;
        }

        if (hasRight) {
            return right;
        }

        return -1f;
    }

    // =========================================
    // 유효한 측정값만 3D 아바타에 전달
    // =========================================

    private static Float positiveOrNull(
            float value
    ) {

        return value > 0f ? value : null;
    }

    // =========================================
    // 기본 신체 치수 표시
    // =========================================

    private String formatBasic(
            String name,
            float value
    ) {

        if (value <= 0f) {
            return name + "\n측정값 없음";
        }

        return String.format(
                Locale.KOREA,
                "%s\n%.1f cm",
                name,
                value
        );
    }

    // =========================================
    // 신체 둘레 카드 표시
    // =========================================

    private String formatCard(
            String name,
            float value
    ) {

        if (value <= 0f) {
            return name + "\n\n측정값 없음";
        }

        return String.format(
                Locale.KOREA,
                "%s\n\n%.1f cm",
                name,
                value
        );
    }
}
