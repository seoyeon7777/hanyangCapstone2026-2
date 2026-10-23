
package com.example.capstone.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.UserBodyProfile;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;

public class MeasurementResultActivity extends AppCompatActivity {

    private static final String UNKNOWN_BODY_TYPE = "미진단";

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

        final float outHeight = height;

        JSONObject parts = new JSONObject();
        putPart(parts, "SHOULDER", shoulder);
        putPart(parts, "CHEST", chest);
        putPart(parts, "WAIST", waist);
        putPart(parts, "HIP", hip);
        putPart(parts, "ARM_LENGTH", armLength);
        putPart(parts, "LEG_LENGTH", legLength);
        putPart(parts, "NECK", neck);
        putPart(parts, "BACK_LENGTH", torso);
        putPart(parts, "UPPER_ARM", upperArm);
        putPart(parts, "FOREARM", forearm);
        putPart(parts, "THIGH", thigh);
        putPart(parts, "CALF", calf);

        // 골격 진단 보조 값 (가슴두께 cm, 어깨 경사각 도). 대표 체형이 되면 추천 서버로 간다
        putPart(parts, BodyEditActivity.PART_CHEST_DEPTH,
                getIntent().getFloatExtra("CHEST_DEPTH", -1f));
        putPart(parts, BodyEditActivity.PART_SHOULDER_SLOPE,
                getIntent().getFloatExtra("SHOULDER_SLOPE_DEG", -1f));

        final float[] typeMeasures = {
                height, shoulder, armLength, torso, legLength
        };

        findViewById(R.id.btnMeasurementDone)
                .setOnClickListener(v ->
                        onSaveBodyClicked(outHeight, parts, typeMeasures));
    }

    private void requestFinalBodyType(
            float[] m,
            Consumer<String> onFinal
    ) {

        for (float value : m) {
            if (value <= 0f) {
                return;
            }
        }

        RecommendApi.finalBodyType(this, m[0], m[1], m[2], m[3], m[4],
                new RecommendApi.Callback<String>() {
                    @Override
                    public void onSuccess(String primary) {
                        onFinal.accept(typeLabel(primary));
                    }

                    @Override
                    public void onError(int code, String message) {
                    }
                });
    }

    private String surveyTypeLabel() {

        switch (BodyTypeActivity.lastSurveyType(this)) {
            case BodyTypeActivity.TYPE_STRAIGHT:
                return "스트레이트";
            case BodyTypeActivity.TYPE_WAVE:
                return "웨이브";
            case BodyTypeActivity.TYPE_NATURAL:
                return "내추럴";
            default:
                return UNKNOWN_BODY_TYPE;
        }
    }

    private String typeLabel(String primary) {

        switch (primary) {
            case "S":
                return "스트레이트";
            case "W":
                return "웨이브";
            case "N":
                return "내추럴";
            default:
                return surveyTypeLabel();
        }
    }

    private void onSaveBodyClicked(
            float heightCm,
            JSONObject parts,
            float[] typeMeasures
    ) {

        if (!LoginSession.isLoggedIn(this)) {
            AppDialog.show(this, false,
                    "로그인이 필요해요",
                    "체형 정보는 로그인 후 내 계정에 저장할 수 있어요.",
                    "취소", null,
                    "로그인하기", () ->
                            startActivity(new Intent(this, LoginMainActivity.class)));
            return;
        }

        String[] bodyType = {surveyTypeLabel()};

        String defaultName =
                BodyProfileStore.nextDefaultName(this);

        TextView tvBodyType = BodySaveConfirmDialog.show(this, heightCm, bodyType[0], parts, defaultName,
                new BodySaveConfirmDialog.Listener() {
                    @Override
                    public void onSave(String name) {
                        BodyProfileStore.Profile saved = BodyProfileStore.save(
                                MeasurementResultActivity.this,
                                null,
                                name, heightCm, 0f, bodyType[0], parts);
                        startActivity(BodySaveCompleteActivity.newIntent(
                                MeasurementResultActivity.this, saved.name));
                        finish();
                    }

                    @Override
                    public void onRestartAll() {
                        Intent intent = new Intent(MeasurementResultActivity.this, FittingActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onRetakeCamera() {
                        Intent intent = new Intent(MeasurementResultActivity.this, BodyMeasurementActivity.class);
                        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                });

        requestFinalBodyType(typeMeasures, finalType -> {
            bodyType[0] = finalType;
            tvBodyType.setText(finalType);
        });
    }

    private static void putPart(
            JSONObject parts,
            String key,
            float value
    ) {

        if (value <= 0f) {
            return;
        }

        try {
            parts.put(key, Math.round(value * 10f) / 10d);
        } catch (Exception ignored) {
        }
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
