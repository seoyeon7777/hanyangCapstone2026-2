package com.example.capstone.camera;

/**
 * 사진 모델(training/README.md 9-4)에 넣을 사람 이미지를 만든다. 학습 때의 전처리와 같은 순서다.
 *
 * 1) 관절로 사람 영역을 사각형으로 자른다 (cropBox)
 * 2) 잘라낸 사진에서 몸 윤곽(마스크)을 구하고, 몸 중심 관절이 지나는 덩어리만 남긴다
 * 3) 몸 밖은 흰색, 몸의 외곽 사각형만 잘라 긴 변이 224의 96%가 되게 줄여 흰 224×224 한가운데에 놓는다
 * 4) 색을 ImageNet 평균·표준편차로 정규화한 CHW 배열로 만든다
 *
 * 마스크(윤곽) 구하기만 안드로이드(ML Kit)가 하고, 나머지는 배열 계산이라 JVM에서도 시험할 수 있다.
 */
public final class PersonCropper {

    public static final int SIZE = 224;
    private static final float FILL = 0.96f;
    private static final float MARGIN = 0.12f;
    private static final float MIN_LIKELIHOOD = 0.3f;
    private static final int MIN_BODY_PIXELS = 3000;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private PersonCropper() {
    }

    public static final class Box {
        public final int x0;
        public final int y0;
        public final int x1;
        public final int y1;

        Box(int x0, int y0, int x1, int y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public int width() {
            return x1 - x0;
        }

        public int height() {
            return y1 - y0;
        }
    }

    public static final class Prepared {
        /** 정규화한 CHW 값 (3 × 224 × 224) */
        public final float[] chw;
        /** 몸 외곽 사각형의 가로 ÷ 세로 (모델의 표 입력) */
        public final float aspect;

        Prepared(float[] chw, float aspect) {
            this.chw = chw;
            this.aspect = aspect;
        }
    }

    /**
     * 관절(자신 있는 것만)을 모두 감싸는 사각형에 위아래 길이의 12%만큼 여유를 둔다.
     * 관절이 20개 미만이면 null.
     */
    public static Box cropBox(Pose pose, int imgW, int imgH) {
        if (pose == null) {
            return null;
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        int n = 0;
        for (int i = 0; i < 33; i++) {
            PoseLandmark l = pose.getPoseLandmark(i);
            if (l == null || l.getInFrameLikelihood() < MIN_LIKELIHOOD) {
                continue;
            }
            float x = l.getPosition().x;
            float y = l.getPosition().y;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            n++;
        }
        if (n < 20) {
            return null;
        }
        float m = MARGIN * (maxY - minY);
        int x0 = Math.max(0, (int) (minX - m));
        int x1 = Math.min(imgW, (int) (maxX + m));
        int y0 = Math.max(0, (int) (minY - m));
        int y1 = Math.min(imgH, (int) (maxY + m));
        if (x1 - x0 < 16 || y1 - y0 < 16) {
            return null;
        }
        return new Box(x0, y0, x1, y1);
    }

    /** 몸통 중심(어깨·엉덩이 네 관절의 평균) 좌표. 없으면 null */
    public static float[] torsoCenter(Pose pose) {
        if (pose == null) {
            return null;
        }
        int[] ids = {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP};
        float sx = 0f, sy = 0f;
        for (int id : ids) {
            PoseLandmark l = pose.getPoseLandmark(id);
            if (l == null) {
                return null;
            }
            sx += l.getPosition().x;
            sy += l.getPosition().y;
        }
        return new float[]{sx / 4f, sy / 4f};
    }

    /**
     * @param argb 잘라낸 사진의 색 (ARGB, 행 우선, cw×ch)
     * @param mask 잘라낸 사진의 몸 확률 (0~1, cw×ch)
     * @param seedX 몸통 중심의 x (잘라낸 사진 기준 픽셀). 마스크 밖이면 가장 큰 덩어리를 쓴다
     * @return 몸을 못 찾으면 null
     */
    public static Prepared prepare(int[] argb, int cw, int ch, float[] mask, float seedX, float seedY) {
        int n = cw * ch;
        if (argb.length < n || mask.length < n) {
            return null;
        }
        int[] label = new int[n];
        int[] queue = new int[n];
        int best = 0;
        int bestCount = 0;
        int seedLabel = 0;
        int sx = Math.min(Math.max(Math.round(seedX), 0), cw - 1);
        int sy = Math.min(Math.max(Math.round(seedY), 0), ch - 1);
        int[] counts = new int[16];
        int labels = 0;
        // 4방향으로 이어진 덩어리에 번호를 붙인다
        for (int start = 0; start < n; start++) {
            if (mask[start] < 0.5f || label[start] != 0) {
                continue;
            }
            labels++;
            int head = 0, tail = 0, count = 0;
            queue[tail++] = start;
            label[start] = labels;
            while (head < tail) {
                int p = queue[head++];
                count++;
                int x = p % cw;
                int y = p / cw;
                if (x > 0 && mask[p - 1] >= 0.5f && label[p - 1] == 0) {
                    label[p - 1] = labels;
                    queue[tail++] = p - 1;
                }
                if (x < cw - 1 && mask[p + 1] >= 0.5f && label[p + 1] == 0) {
                    label[p + 1] = labels;
                    queue[tail++] = p + 1;
                }
                if (y > 0 && mask[p - cw] >= 0.5f && label[p - cw] == 0) {
                    label[p - cw] = labels;
                    queue[tail++] = p - cw;
                }
                if (y < ch - 1 && mask[p + cw] >= 0.5f && label[p + cw] == 0) {
                    label[p + cw] = labels;
                    queue[tail++] = p + cw;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = labels;
            }
        }
        if (labels == 0) {
            return null;
        }
        seedLabel = label[sy * cw + sx];
        int body = seedLabel != 0 ? seedLabel : best;

        int minX = cw, minY = ch, maxX = -1, maxY = -1, size = 0;
        for (int p = 0; p < n; p++) {
            if (label[p] == body) {
                int x = p % cw;
                int y = p / cw;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                size++;
            }
        }
        if (size < MIN_BODY_PIXELS) {
            return null;
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        float k = (SIZE * FILL) / Math.max(w, h);
        int nw = Math.max(1, Math.round(w * k));
        int nh = Math.max(1, Math.round(h * k));

        // 몸 밖은 흰색으로 두고, 몸 사각형을 칸별 평균(면적 평균)으로 줄인다
        float[] sum = new float[nw * nh * 3];
        int[] cnt = new int[nw * nh];
        for (int y = minY; y <= maxY; y++) {
            int oy = Math.min(nh - 1, (int) ((y - minY) * k));
            for (int x = minX; x <= maxX; x++) {
                int p = y * cw + x;
                int ox = Math.min(nw - 1, (int) ((x - minX) * k));
                int cell = oy * nw + ox;
                int r, g, b;
                if (label[p] == body) {
                    int c = argb[p];
                    r = (c >> 16) & 0xFF;
                    g = (c >> 8) & 0xFF;
                    b = c & 0xFF;
                } else {
                    r = g = b = 255;
                }
                sum[cell * 3] += r;
                sum[cell * 3 + 1] += g;
                sum[cell * 3 + 2] += b;
                cnt[cell]++;
            }
        }

        float[] chw = new float[3 * SIZE * SIZE];
        float white0 = (1f - MEAN[0]) / STD[0];
        float white1 = (1f - MEAN[1]) / STD[1];
        float white2 = (1f - MEAN[2]) / STD[2];
        for (int i = 0; i < SIZE * SIZE; i++) {
            chw[i] = white0;
            chw[SIZE * SIZE + i] = white1;
            chw[2 * SIZE * SIZE + i] = white2;
        }
        int offX = (SIZE - nw) / 2;
        int offY = (SIZE - nh) / 2;
        for (int oy = 0; oy < nh; oy++) {
            for (int ox = 0; ox < nw; ox++) {
                int cell = oy * nw + ox;
                if (cnt[cell] == 0) {
                    continue;
                }
                float r = sum[cell * 3] / cnt[cell] / 255f;
                float g = sum[cell * 3 + 1] / cnt[cell] / 255f;
                float b = sum[cell * 3 + 2] / cnt[cell] / 255f;
                int idx = (offY + oy) * SIZE + (offX + ox);
                chw[idx] = (r - MEAN[0]) / STD[0];
                chw[SIZE * SIZE + idx] = (g - MEAN[1]) / STD[1];
                chw[2 * SIZE * SIZE + idx] = (b - MEAN[2]) / STD[2];
            }
        }
        return new Prepared(chw, (float) w / h);
    }
}
