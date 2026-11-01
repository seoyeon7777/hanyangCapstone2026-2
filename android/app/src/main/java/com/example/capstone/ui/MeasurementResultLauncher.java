package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.example.capstone.camera.BodyMeasurementCalculator;
import com.example.capstone.camera.BodyShapeFeatures;
import com.example.capstone.camera.CircumferenceCalculator;
import com.example.capstone.camera.MeasurementSession;

import java.util.Locale;

/**
 * 정면·옆면 사진 분석이 끝나면 측정 결과 화면을 연다.
 * (예전에는 측정 위치를 끌어 맞추는 화면을 거쳤다. 옷·팔 때문에 생기는 오차는 선을 옮겨도 안 줄어서 없앴다.)
 * 가슴·허리·골반 선은 MeasurementSession의 기본 비율(몸 높이의 29/43/52%)을 쓴다.
 */
final class MeasurementResultLauncher {

    private static final float MIN_DEPTH_RATIO = 0.40f;
    private static final float MAX_DEPTH_RATIO = 0.95f;

    private MeasurementResultLauncher() {
    }

    private static float widthOf(MeasurementSession.Silhouette sil, int part) {
        return sil.widthCm(MeasurementSession.heightCm, MeasurementSession.ratios[part]);
    }

    /**
     * 몸통 두께(옆면) ÷ 폭(정면)은 사람 몸에서 대략 0.4~0.95 안에 든다.
     * 벗어나면 정면·옆면이 다른 사람이거나, 같은 사진을 두 번 넣었다는 뜻이다. 문제 없으면 null.
     */
    static String shapeWarning() {
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            float w = widthOf(MeasurementSession.front, i);
            float d = widthOf(MeasurementSession.side, i);
            if (w <= 0f || d <= 0f) {
                continue;
            }
            float ratio = d / w;
            if (ratio < MIN_DEPTH_RATIO || ratio > MAX_DEPTH_RATIO) {
                if (bad.length() > 0) {
                    bad.append(", ");
                }
                bad.append(String.format(Locale.KOREA, "%s %.2f",
                        MeasurementSession.LABELS[i], ratio));
            }
        }
        if (bad.length() == 0) {
            return null;
        }
        return "정면·옆면 사진의 몸 비율이 맞지 않아요 (두께÷폭 " + bad + ").\n"
                + "다른 사람의 사진이거나 같은 사진을 두 번 넣었다면 둘레가 크게 틀릴 수 있어요.";
    }

    /** 결과 화면을 연다. 가슴·허리·골반이 사진에서 잡히지 않으면 안내만 하고 false. */
    static boolean open(Context context) {
        float[] width = new float[3];
        float[] depth = new float[3];
        float[] circ = new float[3];
        for (int i = 0; i < 3; i++) {
            width[i] = widthOf(MeasurementSession.front, i);
            depth[i] = widthOf(MeasurementSession.side, i);
            circ[i] = CircumferenceCalculator.calculateEllipseCircumference(width[i], depth[i]);
            if (circ[i] <= 0f) {
                Toast.makeText(context,
                        MeasurementSession.LABELS[i] + " 위치에서 몸이 잡히지 않아요. 전신이 나온 다른 사진으로 다시 해주세요.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }

        BodyMeasurementCalculator.Result lengths = MeasurementSession.frontLengths;
        Intent intent = new Intent(context, MeasurementResultActivity.class);
        intent.putExtra("HEIGHT", MeasurementSession.heightCm);
        intent.putExtra("SHOULDER", lengths.shoulderWidth);
        intent.putExtra("LEFT_ARM", lengths.leftArmLength);
        intent.putExtra("RIGHT_ARM", lengths.rightArmLength);
        intent.putExtra("TORSO", lengths.torsoLength);
        intent.putExtra("LEFT_LEG", lengths.leftLegLength);
        intent.putExtra("RIGHT_LEG", lengths.rightLegLength);

        intent.putExtra("CHEST_CIRCUMFERENCE", circ[MeasurementSession.CHEST]);
        intent.putExtra("WAIST_CIRCUMFERENCE", circ[MeasurementSession.WAIST]);
        intent.putExtra("HIP_CIRCUMFERENCE", circ[MeasurementSession.HIP]);

        // 사진 모델(딥러닝)이 구한 둘레. 있으면 결과 화면이 사진 계산 값 대신 쓴다
        if (MeasurementSession.modelCirc != null && MeasurementSession.modelCirc.length == 3) {
            intent.putExtra("DL_CHEST", MeasurementSession.modelCirc[0]);
            intent.putExtra("DL_WAIST", MeasurementSession.modelCirc[1]);
            intent.putExtra("DL_HIP", MeasurementSession.modelCirc[2]);
        }

        // 어깨 경사각(도, 정면 윤곽에서 추정). 못 구하면 -1
        intent.putExtra("SHOULDER_SLOPE_DEG",
                BodyShapeFeatures.shoulderSlopeDeg(MeasurementSession.front, lengths));

        intent.putExtra("CHEST_WIDTH", width[MeasurementSession.CHEST]);
        intent.putExtra("CHEST_DEPTH", depth[MeasurementSession.CHEST]);
        intent.putExtra("WAIST_WIDTH", width[MeasurementSession.WAIST]);
        intent.putExtra("WAIST_DEPTH", depth[MeasurementSession.WAIST]);
        intent.putExtra("HIP_WIDTH", width[MeasurementSession.HIP]);
        intent.putExtra("HIP_DEPTH", depth[MeasurementSession.HIP]);

        if (MeasurementSession.frontUri != null) {
            intent.putExtra("FRONT_PHOTO_URI", MeasurementSession.frontUri.toString());
        }
        if (MeasurementSession.sideUri != null) {
            intent.putExtra("SIDE_PHOTO_URI", MeasurementSession.sideUri.toString());
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (MeasurementSession.frontPhoto != null) {
            intent.putExtra("FRONT_PHOTO_PATH", MeasurementSession.frontPhoto.getAbsolutePath());
        }
        if (MeasurementSession.sidePhoto != null) {
            intent.putExtra("SIDE_PHOTO_PATH", MeasurementSession.sidePhoto.getAbsolutePath());
        }

        context.startActivity(intent);
        return true;
    }
}
