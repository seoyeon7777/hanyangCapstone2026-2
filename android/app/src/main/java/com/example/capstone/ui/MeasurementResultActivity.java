
package com.example.capstone.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.camera.MeasurementSession;
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyPrior;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.UserBodyProfile;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

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

        TextView textThigh =
                findViewById(R.id.textResultThigh);

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
        // 사진으로 잰 가슴·허리·골반·허벅지 둘레를 키·몸무게·나이 기준 추정과 맞춘다.
        // 옷·팔 때문에 기준선과 크게 다르면 기준선 값을 쓰고 "추정"으로 표시한다.
        // (입력한 몸무게·나이가 없거나 범위 밖이면 측정값을 그대로 쓴다)
        // =========================================

        // 사진 모델(딥러닝)이 구한 둘레가 있으면 규칙 기반 사진 계산 값 대신 쓴다
        final float dlChest = receivedIntent.getFloatExtra("DL_CHEST", -1f);
        final float dlWaist = receivedIntent.getFloatExtra("DL_WAIST", -1f);
        final float dlHip = receivedIntent.getFloatExtra("DL_HIP", -1f);
        final float dlThigh = receivedIntent.getFloatExtra("DL_THIGH", -1f);
        final boolean modelUsed = dlChest > 0f && dlWaist > 0f && dlHip > 0f;
        final boolean thighModelUsed = modelUsed && dlThigh > 0f;
        if (modelUsed) {
            chest = dlChest;
            waist = dlWaist;
            hip = dlHip;
        }
        if (thighModelUsed) {
            thigh = dlThigh;
        }

        final BodyPrior.Result prior = BodyInput.prior(this, height);
        final boolean[] estimated = new boolean[BodyPrior.PART_KEYS.length];
        final float[] fixed = BodyPrior.reconcile(prior,
                new float[]{chest, waist, hip, thigh}, estimated,
                new boolean[]{modelUsed, modelUsed, modelUsed, thighModelUsed});
        chest = fixed[BodyPrior.CHEST];
        waist = fixed[BodyPrior.WAIST];
        hip = fixed[BodyPrior.HIP];
        thigh = fixed[BodyPrior.THIGH];

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
        for (int i = 0; i < estimated.length; i++) {
            UserBodyProfile.getInstance().setEstimated(i, estimated[i]);
        }

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
                formatCard(estimated[BodyPrior.CHEST] ? "가슴둘레(추정)" : "가슴둘레", chest)
        );

        textWaist.setText(
                formatCard(estimated[BodyPrior.WAIST] ? "허리둘레(추정)" : "허리둘레", waist)
        );

        textHip.setText(
                formatCard(estimated[BodyPrior.HIP] ? "골반둘레(추정)" : "골반둘레", hip)
        );

        textThigh.setText(
                formatCard(estimated[BodyPrior.THIGH] ? "허벅지둘레(추정)" : "허벅지둘레", thigh)
        );

        // 사진이나 키·몸무게·나이로 구하지 못한 항목은 화면에 보이지 않게 한다
        showOnlyIfValid(textHeight, height);
        showOnlyIfValid(textShoulder, shoulder);
        showOnlyIfValid(textArm, armLength);
        showOnlyIfValid(textTorso, torso);
        showOnlyIfValid(textLeg, legLength);

        boolean showChest = chest > 0f;
        boolean showWaist = waist > 0f;
        boolean showHip = hip > 0f;
        boolean showThigh = thigh > 0f;
        applyCardRow(findViewById(R.id.rowCirc1), textChest, showChest, textWaist, showWaist);
        applyCardRow(findViewById(R.id.rowCirc2), textHip, showHip, textThigh, showThigh);

        boolean anyEstimated = false;
        for (boolean e : estimated) {
            anyEstimated |= e;
        }
        // 성인이라 사진 모델이 돌았어야 하는데 결과가 없으면, 사진 분석을 마치지 못했다고 알린다
        final boolean photoModelFailed = prior != null && prior.adult && !modelUsed;
        TextView estNote = findViewById(R.id.textResultEstNote);
        if (photoModelFailed) {
            estNote.setText("사진 분석을 마치지 못해 둘레는 키·몸무게·나이로 어림하거나 간단한 계산으로 구한 값이에요. 정확하지 않을 수 있으니 사진을 다시 골라 보세요.");
        }
        estNote.setVisibility(anyEstimated || photoModelFailed ? View.VISIBLE : View.GONE);

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

            textUpperRatio.setVisibility(View.GONE);
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

            textLowerRatio.setVisibility(View.GONE);
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

            textWhr.setVisibility(View.GONE);
        }

        // 구분선과 제목도 보이는 항목에 맞춘다
        boolean upperShown = textUpperRatio.getVisibility() == View.VISIBLE;
        boolean lowerShown = textLowerRatio.getVisibility() == View.VISIBLE;
        boolean whrShown = textWhr.getVisibility() == View.VISIBLE;
        findViewById(R.id.ratioDiv1).setVisibility(upperShown && lowerShown ? View.VISIBLE : View.GONE);
        findViewById(R.id.ratioDiv2).setVisibility(whrShown && (upperShown || lowerShown) ? View.VISIBLE : View.GONE);
        int ratioVisibility = upperShown || lowerShown || whrShown ? View.VISIBLE : View.GONE;
        findViewById(R.id.headerRatio).setVisibility(ratioVisibility);
        findViewById(R.id.rowRatio).setVisibility(ratioVisibility);

        final float outHeight = height;

        JSONObject parts = new JSONObject();
        putPart(parts, "SHOULDER", shoulder);
        putPart(parts, "CHEST", chest);
        putPart(parts, "WAIST", waist);
        putPart(parts, "HIP", hip);
        putPart(parts, "ARM_LENGTH", armLength);
        putPart(parts, "LEG_LENGTH", legLength);
        putPart(parts, "BACK_LENGTH", torso);
        putPart(parts, "THIGH", thigh);

        // 사진 모델로 구한 값이면 표시한다 (저장된 프로필을 다시 불러올 때 기준선과 달라도 믿는다)
        if (modelUsed && !estimated[BodyPrior.CHEST] && !estimated[BodyPrior.WAIST] && !estimated[BodyPrior.HIP]) {
            try {
                parts.put(BodyProfileStore.PHOTO_MODEL, "1");
            } catch (Exception ignored) {
            }
        }

        // 기준선 값으로 채운 부위는 저장할 때 추정으로 표시한다 (사이즈 알림 여유를 넓힌다)
        StringBuilder estParts = new StringBuilder();
        for (int i = 0; i < estimated.length; i++) {
            if (estimated[i]) {
                estParts.append(estParts.length() > 0 ? "," : "").append(BodyPrior.PART_KEYS[i]);
            }
        }
        if (estParts.length() > 0) {
            try {
                parts.put(BodyProfileStore.EST_PARTS, estParts.toString());
            } catch (Exception ignored) {
            }
        }

        // 골격 진단 보조 값 (가슴두께 cm, 어깨 경사각 도). 대표 체형이 되면 추천 서버로 간다
        putPart(parts, BodyProfileStore.PART_CHEST_DEPTH,
                getIntent().getFloatExtra("CHEST_DEPTH", -1f));
        putPart(parts, BodyProfileStore.PART_SHOULDER_SLOPE,
                getIntent().getFloatExtra("SHOULDER_SLOPE_DEG", -1f));

        findViewById(R.id.btnMeasurementDone)
                .setOnClickListener(v ->
                        onSaveBodyClicked(outHeight, parts));
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

    private void onSaveBodyClicked(
            float heightCm,
            JSONObject parts
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

        // 설문만 한 카드에서 "사진 분석 이어서 하기"로 왔으면 그 카드에 치수를 채운다 (골격·설문 답은 그 카드 것)
        BodyProfileStore.Profile continued =
                BodyProfileStore.find(this, MeasurementSession.continueProfileId);
        if (continued != null && !continued.isSurveyOnly()) {
            continued = null;   // 이미 치수가 있는 카드면 이어서 하지 않고 새 카드로
        }

        final String bodyType;
        final int[] surveyAnswers;
        final String editId;
        String defaultName;

        if (continued != null) {
            bodyType = continued.bodyType;
            surveyAnswers = continued.surveyAnswers;
            editId = continued.id;
            defaultName = continued.name;
        } else {
            // 체형은 저장할 때마다 새 카드로 남는다 (여러 개 저장하고 '내 체형 관리'에서 현재 체형을 고름)
            if (!BodyProfileStore.canAddProfile(this)) {
                AppDialog.alert(this, "체형 저장",
                        "체형은 최대 " + BodyProfileStore.MAX_PROFILES
                                + "개까지 저장할 수 있어요. '내 체형 관리'에서 안 쓰는 체형을 지운 뒤 다시 저장해 주세요.",
                        "확인");
                return;
            }
            bodyType = surveyTypeLabel();
            surveyAnswers = BodyTypeActivity.lastSurveyAnswers(this);
            editId = null;
            defaultName = BodyProfileStore.nextDefaultName(this);
        }

        BodySaveConfirmDialog.show(this, heightCm, bodyType, parts, defaultName,
                new BodySaveConfirmDialog.Listener() {
                    @Override
                    public void onSave(String name) {
                        BodyProfileStore.Profile saved = BodyProfileStore.save(
                                MeasurementResultActivity.this,
                                editId,
                                name, heightCm, 0f, bodyType, parts, surveyAnswers);
                        MeasurementSession.continueProfileId = null;
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
                        Intent intent = new Intent(MeasurementResultActivity.this, PhotoMeasurementActivity.class);
                        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
                        intent.putExtra(PhotoMeasurementActivity.EXTRA_CONTINUE_PROFILE_ID,
                                MeasurementSession.continueProfileId);   // 이어서 하던 카드는 그대로
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
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

    /** 값이 없으면(0 이하) 항목을 아예 숨긴다. */
    private static void showOnlyIfValid(TextView view, float value) {
        view.setVisibility(value > 0f ? View.VISIBLE : View.GONE);
    }

    /**
     * 카드 두 장이 든 줄을 정리한다. 둘 다 없으면 줄을 숨기고,
     * 하나만 없으면 자리를 비워서(보이지 않게) 남은 카드가 다른 줄과 같은 너비를 유지하게 한다.
     */
    private static void applyCardRow(View row, TextView first, boolean showFirst,
                                     TextView second, boolean showSecond) {
        if (!showFirst && !showSecond) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        first.setVisibility(showFirst ? View.VISIBLE : View.INVISIBLE);
        second.setVisibility(showSecond ? View.VISIBLE : View.INVISIBLE);
    }

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
