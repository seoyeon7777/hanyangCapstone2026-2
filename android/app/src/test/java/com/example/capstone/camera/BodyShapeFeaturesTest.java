package com.example.capstone.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 어깨 경사각: 손으로 만든 윤곽(머리 → 가는 목 → 어깨)에서 손계산한 각도와 맞는지 확인한다. */
public class BodyShapeFeaturesTest {

    private static final int W = 200;
    private static final int H = 400;

    /** 위쪽 20줄은 비어 있고, 몸은 20~380줄. 머리 폭 50, 목 폭 16(80~119줄), 그 아래로 어깨가 넓어진다. */
    private static MeasurementSession.Silhouette body() {
        int[] left = new int[H];
        int[] right = new int[H];
        for (int y = 0; y < H; y++) {
            left[y] = -1;
            right[y] = -1;
        }
        for (int y = 20; y <= 380; y++) {
            int half;
            if (y < 80) {
                half = 25;                                  // 머리
            } else if (y < 120) {
                half = 8;                                   // 목: 92 ~ 107
            } else {
                half = 8 + Math.min(52, (y - 119) * 3);     // 어깨 → 몸통
            }
            left[y] = 100 - half;
            right[y] = 99 + half;
        }
        return new MeasurementSession.Silhouette(W, H, 20, 380, left, right);
    }

    @Test
    public void slopeMatchesHandCalculation() {
        // 목은 80~119줄이 같은 폭(16) → 가장 아래 줄 119가 목옆 점. 왼쪽 윤곽 x=92, 오른쪽 x=107
        // 어깨 관절: 줄 140, x = 60(왼쪽) / 140(오른쪽)
        float expectedLeft = (float) Math.toDegrees(Math.atan2(140 - 119, Math.abs(60 - 92)));    // dx 32, dy 21
        float expectedRight = (float) Math.toDegrees(Math.atan2(140 - 119, Math.abs(140 - 107))); // dx 33, dy 21

        float angle = BodyShapeFeatures.shoulderSlopeDeg(
                body(), 60f / H, 60f / W, 140f / H, 140f / W, 140f / H);

        assertEquals((expectedLeft + expectedRight) / 2f, angle, 0.01f);
        assertEquals(32.87f, angle, 0.05f);   // 손계산: (33.27 + 32.47) / 2
    }

    @Test
    public void steeperShoulderGivesLargerAngle() {
        float flat = BodyShapeFeatures.shoulderSlopeDeg(
                body(), 60f / H, 50f / W, 140f / H, 150f / W, 140f / H);   // 어깨가 더 바깥
        float droopy = BodyShapeFeatures.shoulderSlopeDeg(
                body(), 60f / H, 80f / W, 140f / H, 120f / W, 140f / H);   // 어깨가 안쪽
        assertEquals(true, droopy > flat);
    }

    @Test
    public void returnsMinusOneWhenNotMeasurable() {
        // 코가 어깨 바로 위라서 코와 어깨 사이에 목 줄을 찾을 범위가 없으면 구할 수 없다
        assertEquals(-1f, BodyShapeFeatures.shoulderSlopeDeg(
                body(), 130f / H, 60f / W, 140f / H, 140f / W, 140f / H), 0f);
        // 포즈 정보가 없으면 -1
        assertEquals(-1f, BodyShapeFeatures.shoulderSlopeDeg(body(), null), 0f);
        assertEquals(-1f, BodyShapeFeatures.shoulderSlopeDeg(null, new BodyMeasurementCalculator.Result(1, 1, 1, 1, 1, 1)), 0f);
        assertEquals(-1f, BodyShapeFeatures.shoulderSlopeDeg(
                body(), new BodyMeasurementCalculator.Result(1, 1, 1, 1, 1, 1)), 0f);   // 위치가 -1(미설정)
    }
}
