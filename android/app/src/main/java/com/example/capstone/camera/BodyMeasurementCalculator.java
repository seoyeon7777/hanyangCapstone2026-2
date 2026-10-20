package com.example.capstone.camera;

import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class BodyMeasurementCalculator {

    public static class Result {

        public float shoulderWidth;
        public float leftArmLength;
        public float rightArmLength;
        public float torsoLength;
        public float leftLegLength;
        public float rightLegLength;

        public Result(
                float shoulderWidth,
                float leftArmLength,
                float rightArmLength,
                float torsoLength,
                float leftLegLength,
                float rightLegLength
        ) {
            this.shoulderWidth = shoulderWidth;
            this.leftArmLength = leftArmLength;
            this.rightArmLength = rightArmLength;
            this.torsoLength = torsoLength;
            this.leftLegLength = leftLegLength;
            this.rightLegLength = rightLegLength;
        }
    }

    public static Result calculate(
            Pose pose,
            float userHeightCm
    ) {

        if (pose == null || userHeightCm <= 0) {
            return null;
        }

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

        PoseLandmark nose =
                pose.getPoseLandmark(PoseLandmark.NOSE);

        if (
                !valid(leftShoulder) ||
                        !valid(rightShoulder) ||
                        !valid(leftElbow) ||
                        !valid(rightElbow) ||
                        !valid(leftWrist) ||
                        !valid(rightWrist) ||
                        !valid(leftHip) ||
                        !valid(rightHip) ||
                        !valid(leftKnee) ||
                        !valid(rightKnee) ||
                        !valid(leftAnkle) ||
                        !valid(rightAnkle) ||
                        !valid(nose)
        ) {
            return null;
        }

        /*
         * 1. 사진 속 사람의 전체 높이 추정
         *
         * ML Kit에는 머리 꼭대기 landmark가 없어서
         * nose 위치에서 약간 위쪽을 머리 꼭대기로 추정.
         */

        float ankleY =
                (leftAnkle.getPosition().y
                        + rightAnkle.getPosition().y) / 2f;

        float noseY =
                nose.getPosition().y;

        float faceToHeadEstimate =
                distance(
                        leftShoulder.getPosition(),
                        rightShoulder.getPosition()
                ) * 0.35f;

        float estimatedHeadTopY =
                noseY - faceToHeadEstimate;

        float bodyHeightPixels =
                Math.abs(ankleY - estimatedHeadTopY);

        if (bodyHeightPixels <= 0) {
            return null;
        }

        /*
         * 2. 픽셀 → cm 변환 비율
         */

        float cmPerPixel =
                userHeightCm / bodyHeightPixels;

        /*
         * 3. 어깨너비
         */

        float shoulderWidthPixels =
                distance(
                        leftShoulder.getPosition(),
                        rightShoulder.getPosition()
                );

        float shoulderWidth =
                shoulderWidthPixels * cmPerPixel;

        /*
         * 4. 팔 길이
         * 어깨 → 팔꿈치 → 손목
         */

        float leftArmPixels =
                distance(
                        leftShoulder.getPosition(),
                        leftElbow.getPosition()
                )
                        +
                        distance(
                                leftElbow.getPosition(),
                                leftWrist.getPosition()
                        );

        float rightArmPixels =
                distance(
                        rightShoulder.getPosition(),
                        rightElbow.getPosition()
                )
                        +
                        distance(
                                rightElbow.getPosition(),
                                rightWrist.getPosition()
                        );

        float leftArmLength =
                leftArmPixels * cmPerPixel;

        float rightArmLength =
                rightArmPixels * cmPerPixel;

        /*
         * 5. 상체 길이
         *
         * 어깨 중심 → 골반 중심
         */

        PointF shoulderCenter =
                center(
                        leftShoulder.getPosition(),
                        rightShoulder.getPosition()
                );

        PointF hipCenter =
                center(
                        leftHip.getPosition(),
                        rightHip.getPosition()
                );

        float torsoPixels =
                distance(
                        shoulderCenter,
                        hipCenter
                );

        float torsoLength =
                torsoPixels * cmPerPixel;

        /*
         * 6. 다리 길이
         * 골반 → 무릎 → 발목
         */

        float leftLegPixels =
                distance(
                        leftHip.getPosition(),
                        leftKnee.getPosition()
                )
                        +
                        distance(
                                leftKnee.getPosition(),
                                leftAnkle.getPosition()
                        );

        float rightLegPixels =
                distance(
                        rightHip.getPosition(),
                        rightKnee.getPosition()
                )
                        +
                        distance(
                                rightKnee.getPosition(),
                                rightAnkle.getPosition()
                        );

        float leftLegLength =
                leftLegPixels * cmPerPixel;

        float rightLegLength =
                rightLegPixels * cmPerPixel;

        return new Result(
                shoulderWidth,
                leftArmLength,
                rightArmLength,
                torsoLength,
                leftLegLength,
                rightLegLength
        );
    }

    private static boolean valid(
            PoseLandmark landmark
    ) {

        return landmark != null
                &&
                landmark.getInFrameLikelihood() >= 0.5f;
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

    private static PointF center(
            PointF a,
            PointF b
    ) {

        return new PointF(
                (a.x + b.x) / 2f,
                (a.y + b.y) / 2f
        );
    }
}