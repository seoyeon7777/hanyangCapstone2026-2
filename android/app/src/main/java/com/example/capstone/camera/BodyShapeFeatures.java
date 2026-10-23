package com.example.capstone.camera;

/**
 * 골격 진단 보조 값을 사진에서 뽑는다 (박선미·정은영 2024 논문의 진단 요소).
 * - 가슴두께: 옆면 윤곽의 몸통 앞뒤 두께. 측정선 조정 화면의 "두께" 값을 그대로 쓴다.
 * - 어깨 경사각: 정면 윤곽에서 목 옆 점과 어깨 점을 이은 선의 기울기 (이 클래스).
 *
 * 윤곽(마스크)과 관절 위치는 같은 사진에서 나온 값이라 0~1 비율로 맞춘다.
 * 머리카락·옷·자세에 민감한 추정값이라, 서버에서도 작은 가중치로만 쓴다.
 */
public final class BodyShapeFeatures {

    /** 목이 가장 좁은 줄을 찾을 범위: 코 아래 ~ 어깨 위 (몸 높이 대비) */
    private static final float NECK_SEARCH_BELOW_NOSE = 0.03f;
    private static final float NECK_SEARCH_ABOVE_SHOULDER = 0.01f;

    private BodyShapeFeatures() {
    }

    /**
     * 어깨 경사각(도). 왼쪽·오른쪽 평균.
     * 목옆 점 = 코와 어깨 사이에서 몸이 가장 좁은 줄의 윤곽 끝,
     * 어깨 점 = 어깨 관절 위치 (관절은 어깨 끝보다 조금 안쪽·아래라서 실제보다 약간 작게 나올 수 있다).
     * 구할 수 없으면(윤곽·관절 정보 없음, 목이 안 보임) -1.
     */
    public static float shoulderSlopeDeg(
            MeasurementSession.Silhouette sil,
            BodyMeasurementCalculator.Result pose
    ) {

        if (sil == null || pose == null
                || pose.noseY < 0f || pose.leftShoulderY < 0f || pose.rightShoulderY < 0f) {
            return -1f;
        }

        // 이미지에서 왼쪽에 보이는 어깨/오른쪽에 보이는 어깨 (윤곽의 왼쪽/오른쪽 끝과 짝을 맞춘다)
        boolean leftIsFirst = pose.leftShoulderX <= pose.rightShoulderX;
        float imgLeftX = leftIsFirst ? pose.leftShoulderX : pose.rightShoulderX;
        float imgLeftY = leftIsFirst ? pose.leftShoulderY : pose.rightShoulderY;
        float imgRightX = leftIsFirst ? pose.rightShoulderX : pose.leftShoulderX;
        float imgRightY = leftIsFirst ? pose.rightShoulderY : pose.leftShoulderY;

        return shoulderSlopeDeg(sil, pose.noseY, imgLeftX, imgLeftY, imgRightX, imgRightY);
    }

    /** 좌표는 모두 이미지 안의 위치(0~1). imgLeft = 이미지에서 왼쪽에 있는 어깨. */
    static float shoulderSlopeDeg(
            MeasurementSession.Silhouette sil,
            float noseY,
            float imgLeftX, float imgLeftY,
            float imgRightX, float imgRightY
    ) {

        float bodyRows = sil.bottom - sil.top;

        if (bodyRows <= 0f) {
            return -1f;
        }

        int shoulderRow = Math.round(Math.min(imgLeftY, imgRightY) * sil.height);
        int from = Math.round(noseY * sil.height + NECK_SEARCH_BELOW_NOSE * bodyRows);
        int to = Math.round(shoulderRow - NECK_SEARCH_ABOVE_SHOULDER * bodyRows);

        from = Math.max(sil.top, from);
        to = Math.min(sil.height - 1, to);

        // 목: 코와 어깨 사이에서 가장 좁은 줄 (턱선보다 아래, 어깨선보다 위)
        int neckRow = -1;
        float narrowest = Float.MAX_VALUE;

        for (int y = from; y <= to; y++) {
            if (sil.left[y] < 0 || sil.right[y] < 0) {
                continue;
            }

            float w = sil.right[y] - sil.left[y] + 1;

            // 같은 폭이면 더 아래 줄을 고른다 (목옆 점은 목이 어깨와 만나는 목 아랫부분)
            if (w <= narrowest) {
                narrowest = w;
                neckRow = y;
            }
        }

        if (neckRow < 0) {
            return -1f;
        }

        float leftAngle = sideAngle(
                sil.left[neckRow], neckRow,
                imgLeftX * sil.width, imgLeftY * sil.height);

        float rightAngle = sideAngle(
                sil.right[neckRow], neckRow,
                imgRightX * sil.width, imgRightY * sil.height);

        if (leftAngle < 0f || rightAngle < 0f) {
            return -1f;
        }

        return (leftAngle + rightAngle) / 2f;
    }

    /** 목옆 점(neckX, neckY)에서 어깨 점(shoulderX, shoulderY)으로 가는 선이 수평과 이루는 각(도). 이상하면 -1 */
    private static float sideAngle(float neckX, float neckY, float shoulderX, float shoulderY) {

        float dx = Math.abs(shoulderX - neckX);
        float dy = shoulderY - neckY;      // 어깨가 목옆보다 아래(+)여야 한다

        if (dx < 1f || dy <= 0f) {
            return -1f;
        }

        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }
}
