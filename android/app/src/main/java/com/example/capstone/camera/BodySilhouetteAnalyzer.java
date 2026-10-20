package com.example.capstone.camera;

import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.segmentation.SegmentationMask;

import java.nio.ByteBuffer;

public class BodySilhouetteAnalyzer {

    // 한 사진에서 얻은 신체 폭 정보
    public static class Result {

        public float chest;
        public float waist;
        public float hip;
        public float neck;

        public float leftUpperArm;
        public float rightUpperArm;

        public float leftForearm;
        public float rightForearm;

        public float leftThigh;
        public float rightThigh;

        public float leftCalf;
        public float rightCalf;

        public Result(
                float chest,
                float waist,
                float hip,
                float neck,
                float leftUpperArm,
                float rightUpperArm,
                float leftForearm,
                float rightForearm,
                float leftThigh,
                float rightThigh,
                float leftCalf,
                float rightCalf
        ) {

            this.chest = chest;
            this.waist = waist;
            this.hip = hip;
            this.neck = neck;

            this.leftUpperArm = leftUpperArm;
            this.rightUpperArm = rightUpperArm;

            this.leftForearm = leftForearm;
            this.rightForearm = rightForearm;

            this.leftThigh = leftThigh;
            this.rightThigh = rightThigh;

            this.leftCalf = leftCalf;
            this.rightCalf = rightCalf;
        }
    }


    public static Result analyze(
            Pose pose,
            SegmentationMask mask,
            float userHeightCm
    ) {

        if (pose == null || mask == null || userHeightCm <= 0) {
            return null;
        }


        // ==========================================
        // Pose landmark
        // ==========================================

        PoseLandmark nose =
                pose.getPoseLandmark(PoseLandmark.NOSE);

        PoseLandmark leftShoulder =
                pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);

        PoseLandmark rightShoulder =
                pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        PoseLandmark leftElbow =
                pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);

        PoseLandmark rightElbow =
                pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);

        PoseLandmark leftWrist =
                pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);

        PoseLandmark rightWrist =
                pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);

        PoseLandmark leftHip =
                pose.getPoseLandmark(PoseLandmark.LEFT_HIP);

        PoseLandmark rightHip =
                pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

        PoseLandmark leftKnee =
                pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);

        PoseLandmark rightKnee =
                pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);

        PoseLandmark leftAnkle =
                pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        PoseLandmark rightAnkle =
                pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);


        if (!valid(nose)
                || !valid(leftShoulder)
                || !valid(rightShoulder)
                || !valid(leftElbow)
                || !valid(rightElbow)
                || !valid(leftWrist)
                || !valid(rightWrist)
                || !valid(leftHip)
                || !valid(rightHip)
                || !valid(leftKnee)
                || !valid(rightKnee)
                || !valid(leftAnkle)
                || !valid(rightAnkle)) {

            return null;
        }


        int maskWidth = mask.getWidth();
        int maskHeight = mask.getHeight();

        ByteBuffer buffer = mask.getBuffer();


        // ==========================================
        // 사진의 좌표계를 segmentation mask 좌표계로 변환
        //
        // Pose 좌표의 실제 이미지 크기를 ML Kit Pose 객체에서
        // 직접 얻을 수 없기 때문에 landmark 범위를 이용해서
        // 신체 높이를 기준으로 cm/pixel을 계산한다.
        // ==========================================

        PointF leftShoulderP = leftShoulder.getPosition();
        PointF rightShoulderP = rightShoulder.getPosition();

        PointF leftHipP = leftHip.getPosition();
        PointF rightHipP = rightHip.getPosition();

        PointF leftAnkleP = leftAnkle.getPosition();
        PointF rightAnkleP = rightAnkle.getPosition();


        float shoulderCenterY =
                (leftShoulderP.y + rightShoulderP.y) / 2f;

        float hipCenterY =
                (leftHipP.y + rightHipP.y) / 2f;

        float ankleY =
                (leftAnkleP.y + rightAnkleP.y) / 2f;


        // 기존 BodyMeasurementCalculator와 동일한 방식으로
        // 머리 꼭대기 추정
        float shoulderPixels =
                distance(leftShoulderP, rightShoulderP);

        float estimatedHeadTopY =
                nose.getPosition().y
                        - (shoulderPixels * 0.35f);

        float bodyHeightPixels =
                Math.abs(ankleY - estimatedHeadTopY);

        if (bodyHeightPixels <= 0) {
            return null;
        }


        float cmPerPosePixel =
                userHeightCm / bodyHeightPixels;


        /*
         * SegmentationMask와 Pose의 좌표 크기가 다를 수 있으므로
         * mask에서의 신체 높이도 찾아 별도의 cm 비율을 만든다.
         */
        int maskTop = findTop(buffer, maskWidth, maskHeight);
        int maskBottom = findBottom(buffer, maskWidth, maskHeight);

        if (maskTop < 0 || maskBottom < 0 || maskBottom <= maskTop) {
            return null;
        }

        float bodyHeightMaskPixels =
                maskBottom - maskTop;

        float cmPerMaskPixel =
                userHeightCm / bodyHeightMaskPixels;


        // ==========================================
        // 신체 높이 비율
        // ==========================================

        float torsoHeight =
                hipCenterY - shoulderCenterY;


        // 가슴:
        // 어깨에서 골반 방향 약 25%
        float chestY =
                shoulderCenterY
                        + torsoHeight * 0.25f;


        // 허리:
        // 어깨에서 골반 방향 약 65%
        float waistY =
                shoulderCenterY
                        + torsoHeight * 0.65f;


        // 골반
        float hipY =
                hipCenterY;


        // 목:
        // 어깨보다 약간 위
        float neckY =
                shoulderCenterY
                        - torsoHeight * 0.12f;


        // ==========================================
        // Pose Y → mask Y
        // ==========================================

        float poseBodyTop = estimatedHeadTopY;

        float chestRatio =
                (chestY - poseBodyTop)
                        / bodyHeightPixels;

        float waistRatio =
                (waistY - poseBodyTop)
                        / bodyHeightPixels;

        float hipRatio =
                (hipY - poseBodyTop)
                        / bodyHeightPixels;

        float neckRatio =
                (neckY - poseBodyTop)
                        / bodyHeightPixels;


        int chestMaskY =
                ratioToMaskY(
                        chestRatio,
                        maskTop,
                        maskBottom
                );

        int waistMaskY =
                ratioToMaskY(
                        waistRatio,
                        maskTop,
                        maskBottom
                );

        int hipMaskY =
                ratioToMaskY(
                        hipRatio,
                        maskTop,
                        maskBottom
                );

        int neckMaskY =
                ratioToMaskY(
                        neckRatio,
                        maskTop,
                        maskBottom
                );


        // ==========================================
        // 몸통 폭
        // ==========================================

        float chest =
                measureRowWidth(
                        buffer,
                        maskWidth,
                        maskHeight,
                        chestMaskY
                ) * cmPerMaskPixel;


        float waist =
                measureRowWidth(
                        buffer,
                        maskWidth,
                        maskHeight,
                        waistMaskY
                ) * cmPerMaskPixel;


        float hip =
                measureRowWidth(
                        buffer,
                        maskWidth,
                        maskHeight,
                        hipMaskY
                ) * cmPerMaskPixel;


        float neck =
                measureRowWidth(
                        buffer,
                        maskWidth,
                        maskHeight,
                        neckMaskY
                ) * cmPerMaskPixel;


        // ==========================================
        // 팔
        // ==========================================

        float leftUpperArm =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        leftShoulder.getPosition(),
                        leftElbow.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        float rightUpperArm =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        rightShoulder.getPosition(),
                        rightElbow.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        float leftForearm =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        leftElbow.getPosition(),
                        leftWrist.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        float rightForearm =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        rightElbow.getPosition(),
                        rightWrist.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        // ==========================================
        // 다리
        // ==========================================

        float leftThigh =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        leftHip.getPosition(),
                        leftKnee.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        float rightThigh =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        rightHip.getPosition(),
                        rightKnee.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        float leftCalf =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        leftKnee.getPosition(),
                        leftAnkle.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        float rightCalf =
                measureLimbThickness(
                        buffer,
                        maskWidth,
                        maskHeight,
                        rightKnee.getPosition(),
                        rightAnkle.getPosition(),
                        estimatedHeadTopY,
                        bodyHeightPixels,
                        maskTop,
                        maskBottom,
                        cmPerMaskPixel
                );


        return new Result(
                chest,
                waist,
                hip,
                neck,
                leftUpperArm,
                rightUpperArm,
                leftForearm,
                rightForearm,
                leftThigh,
                rightThigh,
                leftCalf,
                rightCalf
        );
    }


    // ==========================================
    // 한 가로줄에서 사람 영역 폭 측정
    // ==========================================

    private static float measureRowWidth(
            ByteBuffer buffer,
            int width,
            int height,
            int y
    ) {

        if (y < 0 || y >= height) {
            return 0f;
        }

        int left = -1;
        int right = -1;

        for (int x = 0; x < width; x++) {

            float confidence =
                    getConfidence(
                            buffer,
                            width,
                            x,
                            y
                    );

            if (confidence >= 0.5f) {

                if (left == -1) {
                    left = x;
                }

                right = x;
            }
        }

        if (left == -1 || right == -1) {
            return 0f;
        }

        return right - left;
    }


    // ==========================================
    // 팔/다리 두께
    //
    // 현재는 두 관절 중간 높이에서
    // 실루엣 가로폭을 읽는다.
    // ==========================================

    private static float measureLimbThickness(
            ByteBuffer buffer,
            int width,
            int height,
            PointF start,
            PointF end,
            float poseTop,
            float poseHeight,
            int maskTop,
            int maskBottom,
            float cmPerMaskPixel
    ) {

        PointF middle =
                new PointF(
                        (start.x + end.x) / 2f,
                        (start.y + end.y) / 2f
                );


        float ratio =
                (middle.y - poseTop)
                        / poseHeight;


        int maskY =
                ratioToMaskY(
                        ratio,
                        maskTop,
                        maskBottom
                );


        float widthPixels =
                measureRowWidth(
                        buffer,
                        width,
                        height,
                        maskY
                );


        return widthPixels * cmPerMaskPixel;
    }


    private static int ratioToMaskY(
            float ratio,
            int top,
            int bottom
    ) {

        ratio =
                Math.max(
                        0f,
                        Math.min(1f, ratio)
                );

        return Math.round(
                top + ratio * (bottom - top)
        );
    }


    // ==========================================
    // mask 가장 위 사람 픽셀
    // ==========================================

    private static int findTop(
            ByteBuffer buffer,
            int width,
            int height
    ) {

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                if (getConfidence(
                        buffer,
                        width,
                        x,
                        y
                ) >= 0.5f) {

                    return y;
                }
            }
        }

        return -1;
    }


    // ==========================================
    // mask 가장 아래 사람 픽셀
    // ==========================================

    private static int findBottom(
            ByteBuffer buffer,
            int width,
            int height
    ) {

        for (int y = height - 1; y >= 0; y--) {

            for (int x = 0; x < width; x++) {

                if (getConfidence(
                        buffer,
                        width,
                        x,
                        y
                ) >= 0.5f) {

                    return y;
                }
            }
        }

        return -1;
    }


    // ==========================================
    // Segmentation confidence
    // ==========================================

    private static float getConfidence(
            ByteBuffer buffer,
            int width,
            int x,
            int y
    ) {

        int index =
                (y * width + x) * 4;

        return buffer.getFloat(index);
    }


    private static boolean valid(
            PoseLandmark landmark
    ) {

        return landmark != null
                && landmark.getInFrameLikelihood() >= 0.5f;
    }


    private static float distance(
            PointF a,
            PointF b
    ) {

        float dx = a.x - b.x;
        float dy = a.y - b.y;

        return (float) Math.sqrt(
                dx * dx + dy * dy
        );
    }
}