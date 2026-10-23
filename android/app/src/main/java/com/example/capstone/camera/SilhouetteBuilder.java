package com.example.capstone.camera;

import com.google.mlkit.vision.segmentation.SegmentationMask;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 사람 윤곽 마스크(ML Kit)를 가로줄마다의 몸 폭 요약({@link MeasurementSession.Silhouette})으로 바꾼다.
 *
 * 마스크에는 몸 말고 배경의 물건(선반·침대·옷더미)이 사람처럼 잡히기도 한다.
 * 그래서 "화면 가운데에 가까운 덩어리"가 아니라 관절로 구한 몸의 중심선({@link Axis})이 지나는 덩어리를
 * 몸으로 고르고, 중심선 양쪽 일정 거리 밖은 보지 않는다. 중심선을 모르면(관절을 못 찾으면) 예전처럼
 * 화면 가운데 기준으로 고른다.
 */
public final class SilhouetteBuilder {

    private static final float PERSON_CONFIDENCE = 0.5f;

    /** 중심선에서 좌우로 이만큼(몸 키 대비)까지만 몸으로 본다. 160cm 기준 약 ±26cm = 폭 51cm */
    static final float MAX_HALF_WIDTH_RATIO = 0.16f;
    /** 중심선에서 이보다 멀리(몸 키 대비) 떨어진 덩어리는 몸이 아니라 배경 물건으로 본다. 160cm 기준 약 19cm.
     *  다리를 벌리고 서면 중심선이 두 다리 사이에 있어서 몸 덩어리도 중심선에서 조금 떨어질 수 있다. */
    static final float MAX_GAP_RATIO = 0.12f;
    /** 몸 키를 모를 때: 화면 높이의 이 비율을 몸 키로 가정 */
    static final float DEFAULT_BODY_FRACTION = 0.8f;

    /** 몸의 중심선. x는 이미지 안의 위치(0~1), bodyHeight는 키가 이미지 높이에서 차지하는 비율(0~1, 모르면 -1) */
    public static final class Axis {
        public final float x;
        public final float bodyHeight;

        public Axis(float x, float bodyHeight) {
            this.x = x;
            this.bodyHeight = bodyHeight;
        }
    }

    private SilhouetteBuilder() {
    }

    /** 사람이 없으면 null. axis가 null이면 화면 가운데 기준 (예전 방식) */
    public static MeasurementSession.Silhouette build(SegmentationMask mask, Axis axis) {

        if (mask == null) {
            return null;
        }

        ByteBuffer buffer = mask.getBuffer();
        buffer.order(ByteOrder.nativeOrder());
        buffer.rewind();

        return build(buffer, mask.getWidth(), mask.getHeight(), axis);
    }

    /** 마스크 값(float, 한 픽셀 4바이트)을 직접 받는 버전. 단위 테스트에서도 쓴다. */
    static MeasurementSession.Silhouette build(ByteBuffer buffer, int width, int height, Axis axis) {

        if (width <= 0 || height <= 0) {
            return null;
        }

        float axisX = axis == null ? width / 2f : axis.x * width;
        float bodyRows = (axis != null && axis.bodyHeight > 0f)
                ? axis.bodyHeight * height
                : DEFAULT_BODY_FRACTION * height;

        // 중심선을 알 때만 좌우를 제한한다 (모르면 화면 전체)
        int windowLeft = 0;
        int windowRight = width - 1;
        float maxGap = Float.MAX_VALUE;

        if (axis != null) {
            int half = Math.round(MAX_HALF_WIDTH_RATIO * bodyRows);
            windowLeft = Math.max(0, Math.round(axisX) - half);
            windowRight = Math.min(width - 1, Math.round(axisX) + half);
            maxGap = MAX_GAP_RATIO * bodyRows;
        }

        int[] left = new int[height];
        int[] right = new int[height];
        int top = -1;
        int bottom = -1;

        for (int y = 0; y < height; y++) {

            findSegment(buffer, width, y, axisX, windowLeft, windowRight, maxGap, left, right);

            // 위·아래 끝은 "몸으로 고른 덩어리가 있는 줄"로 정한다 (배경 물건이 끝을 늘리지 않게)
            if (left[y] >= 0) {
                if (top == -1) {
                    top = y;
                }
                bottom = y;
            }
        }

        if (top < 0 || bottom <= top) {
            return null;
        }

        return new MeasurementSession.Silhouette(width, height, top, bottom, left, right);
    }

    private static float confidenceAt(ByteBuffer buffer, int width, int x, int y) {
        return buffer.getFloat((y * width + x) * 4);
    }

    /**
     * 줄 y에서 [from, to] 범위 안의 사람 덩어리 중 중심선(axisX)에 가장 가까운 것의 양 끝을 left/right[y]에 넣는다.
     * 중심선이 덩어리 안에 있으면 그 덩어리, 아니면 중심선과의 거리가 가장 짧은 덩어리(maxGap 이내). 없으면 -1.
     */
    private static void findSegment(
            ByteBuffer buffer, int width, int y, float axisX, int from, int to, float maxGap,
            int[] left, int[] right) {

        int bestStart = -1;
        int bestEnd = -1;
        int currentStart = -1;
        float bestDistance = Float.MAX_VALUE;

        for (int x = from; x <= to + 1; x++) {

            boolean isPerson = x <= to
                    && confidenceAt(buffer, width, x, y) >= PERSON_CONFIDENCE;

            if (isPerson && currentStart == -1) {
                currentStart = x;
            }

            if (!isPerson && currentStart != -1) {

                int end = x - 1;
                float distance = axisX < currentStart ? currentStart - axisX
                        : axisX > end ? axisX - end : 0f;

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestStart = currentStart;
                    bestEnd = end;
                }

                currentStart = -1;
            }
        }

        if (bestDistance > maxGap) {   // 중심선에서 너무 먼 덩어리뿐이면 이 줄에는 몸이 없다
            bestStart = -1;
            bestEnd = -1;
        }

        left[y] = bestStart;
        right[y] = bestEnd;
    }
}
