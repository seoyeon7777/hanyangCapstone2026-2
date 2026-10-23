package com.example.capstone.camera;

import com.google.mlkit.vision.segmentation.SegmentationMask;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 사람 윤곽 마스크(ML Kit)를 가로줄마다의 몸 폭 요약({@link MeasurementSession.Silhouette})으로 바꾼다. */
public final class SilhouetteBuilder {

    private static final float PERSON_CONFIDENCE = 0.5f;

    private SilhouetteBuilder() {
    }

    /** 사람이 없으면 null */
    public static MeasurementSession.Silhouette build(SegmentationMask mask) {

        if (mask == null) {
            return null;
        }

        int width = mask.getWidth();
        int height = mask.getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        ByteBuffer buffer = mask.getBuffer();
        buffer.order(ByteOrder.nativeOrder());
        buffer.rewind();

        int[] left = new int[height];
        int[] right = new int[height];
        int top = -1;
        int bottom = -1;

        for (int y = 0; y < height; y++) {

            findCentralSegment(buffer, width, y, left, right);

            // 위·아래 끝은 "줄에 사람이 한 픽셀이라도 있는가"로 정한다
            if (rowHasPerson(buffer, width, y)) {
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

    private static boolean rowHasPerson(ByteBuffer buffer, int width, int y) {
        for (int x = 0; x < width; x++) {
            if (confidenceAt(buffer, width, x, y) >= PERSON_CONFIDENCE) {
                return true;
            }
        }
        return false;
    }

    /** 줄에서 화면 가운데에 가장 가까운 사람 덩어리의 양 끝을 left/right[y]에 넣는다. */
    private static void findCentralSegment(
            ByteBuffer buffer, int width, int y, int[] left, int[] right) {

        int bestStart = -1;
        int bestEnd = -1;
        int currentStart = -1;

        float centerX = width / 2f;
        float bestDistance = Float.MAX_VALUE;

        for (int x = 0; x <= width; x++) {

            boolean isPerson = x < width
                    && confidenceAt(buffer, width, x, y) >= PERSON_CONFIDENCE;

            if (isPerson && currentStart == -1) {
                currentStart = x;
            }

            if (!isPerson && currentStart != -1) {

                int end = x - 1;
                float distance = Math.abs((currentStart + end) / 2f - centerX);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestStart = currentStart;
                    bestEnd = end;
                }

                currentStart = -1;
            }
        }

        left[y] = bestStart;
        right[y] = bestEnd;
    }
}
