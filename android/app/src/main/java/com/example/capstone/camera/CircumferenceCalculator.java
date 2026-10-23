package com.example.capstone.camera;

public class CircumferenceCalculator {

    public static class Result {

        public float chestCircumference;
        public float waistCircumference;
        public float hipCircumference;

        public float neckCircumference;

        public float leftUpperArmCircumference;
        public float rightUpperArmCircumference;

        public float leftForearmCircumference;
        public float rightForearmCircumference;

        public float leftThighCircumference;
        public float rightThighCircumference;

        public float leftCalfCircumference;
        public float rightCalfCircumference;


        public Result(
                float chestCircumference,
                float waistCircumference,
                float hipCircumference,
                float neckCircumference,

                float leftUpperArmCircumference,
                float rightUpperArmCircumference,

                float leftForearmCircumference,
                float rightForearmCircumference,

                float leftThighCircumference,
                float rightThighCircumference,

                float leftCalfCircumference,
                float rightCalfCircumference
        ) {

            this.chestCircumference = chestCircumference;
            this.waistCircumference = waistCircumference;
            this.hipCircumference = hipCircumference;

            this.neckCircumference = neckCircumference;

            this.leftUpperArmCircumference =
                    leftUpperArmCircumference;

            this.rightUpperArmCircumference =
                    rightUpperArmCircumference;

            this.leftForearmCircumference =
                    leftForearmCircumference;

            this.rightForearmCircumference =
                    rightForearmCircumference;

            this.leftThighCircumference =
                    leftThighCircumference;

            this.rightThighCircumference =
                    rightThighCircumference;

            this.leftCalfCircumference =
                    leftCalfCircumference;

            this.rightCalfCircumference =
                    rightCalfCircumference;
        }
    }


    // =============================================
    // 정면 폭 + 옆면 두께 → 타원 둘레
    // =============================================
    public static float calculateEllipseCircumference(
            float widthCm,
            float depthCm
    ) {

        if (widthCm <= 0 || depthCm <= 0) {
            return 0f;
        }

        // 타원의 반지름
        double a = widthCm / 2.0;
        double b = depthCm / 2.0;

        /*
         * Ramanujan 타원 둘레 근사식
         *
         * C ≈ π [
         * 3(a+b)
         * -
         * √((3a+b)(a+3b))
         * ]
         */

        double circumference =
                Math.PI *
                        (
                                3.0 * (a + b)
                                        -
                                        Math.sqrt(
                                                (3.0 * a + b)
                                                        *
                                                        (a + 3.0 * b)
                                        )
                        );

        return (float) circumference;
    }


    // =============================================
    // 모든 부위 둘레 계산
    // =============================================
    public static Result calculate(
            float chestWidth,
            float chestDepth,

            float waistWidth,
            float waistDepth,

            float hipWidth,
            float hipDepth,

            float neckWidth,
            float neckDepth,

            float leftUpperArmWidth,
            float leftUpperArmDepth,

            float rightUpperArmWidth,
            float rightUpperArmDepth,

            float leftForearmWidth,
            float leftForearmDepth,

            float rightForearmWidth,
            float rightForearmDepth,

            float leftThighWidth,
            float leftThighDepth,

            float rightThighWidth,
            float rightThighDepth,

            float leftCalfWidth,
            float leftCalfDepth,

            float rightCalfWidth,
            float rightCalfDepth
    ) {


        float chest =
                calculateEllipseCircumference(
                        chestWidth,
                        chestDepth
                );


        float waist =
                calculateEllipseCircumference(
                        waistWidth,
                        waistDepth
                );


        float hip =
                calculateEllipseCircumference(
                        hipWidth,
                        hipDepth
                );


        float neck =
                calculateEllipseCircumference(
                        neckWidth,
                        neckDepth
                );


        float leftUpperArm =
                calculateEllipseCircumference(
                        leftUpperArmWidth,
                        leftUpperArmDepth
                );


        float rightUpperArm =
                calculateEllipseCircumference(
                        rightUpperArmWidth,
                        rightUpperArmDepth
                );


        float leftForearm =
                calculateEllipseCircumference(
                        leftForearmWidth,
                        leftForearmDepth
                );


        float rightForearm =
                calculateEllipseCircumference(
                        rightForearmWidth,
                        rightForearmDepth
                );


        float leftThigh =
                calculateEllipseCircumference(
                        leftThighWidth,
                        leftThighDepth
                );


        float rightThigh =
                calculateEllipseCircumference(
                        rightThighWidth,
                        rightThighDepth
                );


        float leftCalf =
                calculateEllipseCircumference(
                        leftCalfWidth,
                        leftCalfDepth
                );


        float rightCalf =
                calculateEllipseCircumference(
                        rightCalfWidth,
                        rightCalfDepth
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
}