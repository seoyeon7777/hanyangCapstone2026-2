package com.example.capstone.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 사진 모델 입력(사람 이미지)을 만드는 전처리: 몸 덩어리만 남기고 흰 배경 224×224 한가운데에 놓는지. */
public class PersonCropperTest {

    private static final int W = 300;
    private static final int H = 400;
    private static final int RED = 0xFFC83232;

    private static float[] maskWith(int[]... rects) {
        float[] m = new float[W * H];
        for (int[] r : rects) {                       // x0, x1, y0, y1 (포함)
            for (int y = r[2]; y <= r[3]; y++) {
                for (int x = r[0]; x <= r[1]; x++) {
                    m[y * W + x] = 1f;
                }
            }
        }
        return m;
    }

    private static int[] solid(int color) {
        int[] a = new int[W * H];
        java.util.Arrays.fill(a, color);
        return a;
    }

    @Test
    public void body_is_cropped_centered_and_aspect_is_width_over_height() {
        // 몸: x 100~199 (100px), y 50~349 (300px) → 가로세로 비율 1/3
        float[] mask = maskWith(new int[]{100, 199, 50, 349});
        PersonCropper.Prepared p = PersonCropper.prepare(solid(RED), W, H, mask, 150f, 200f);
        assertNotNull(p);
        assertEquals(100f / 300f, p.aspect, 0.001f);
        int n = PersonCropper.SIZE * PersonCropper.SIZE;
        assertEquals(3 * n, p.chw.length);
        // 가운데 픽셀은 몸(빨강), 왼쪽 가장자리는 흰 배경: 흰색은 (1-mean)/std로 정규화된 값
        int center = (PersonCropper.SIZE / 2) * PersonCropper.SIZE + PersonCropper.SIZE / 2;
        int corner = 0;
        float whiteR = (1f - 0.485f) / 0.229f;
        assertEquals(whiteR, p.chw[corner], 0.01f);
        float redR = ((0xC8 / 255f) - 0.485f) / 0.229f;
        assertEquals(redR, p.chw[center], 0.05f);
        // 긴 변(높이 300)이 224의 96%(약 215px)가 된다: 위쪽에서 몸이 시작하는 줄 ≈ (224-215)/2
        int top = -1;
        for (int y = 0; y < PersonCropper.SIZE; y++) {
            if (Math.abs(p.chw[y * PersonCropper.SIZE + PersonCropper.SIZE / 2] - whiteR) > 0.5f) {
                top = y;
                break;
            }
        }
        assertTrue("몸이 위에서 4~6줄 사이에서 시작해야 함: " + top, top >= 3 && top <= 7);
    }

    @Test
    public void background_blob_is_dropped_when_seed_is_on_the_body() {
        // 몸(가운데)과, 떨어져 있는 큰 배경 물건(왼쪽). 몸통 중심(seed)이 몸 위에 있으면 몸만 남는다
        float[] mask = maskWith(new int[]{150, 199, 50, 349}, new int[]{0, 90, 20, 380});
        PersonCropper.Prepared p = PersonCropper.prepare(solid(RED), W, H, mask, 175f, 200f);
        assertNotNull(p);
        assertEquals(50f / 300f, p.aspect, 0.001f);
    }

    @Test
    public void seed_outside_mask_falls_back_to_largest_component() {
        float[] mask = maskWith(new int[]{100, 199, 50, 349}, new int[]{0, 20, 0, 20});
        PersonCropper.Prepared p = PersonCropper.prepare(solid(RED), W, H, mask, 250f, 380f);
        assertNotNull(p);
        assertEquals(100f / 300f, p.aspect, 0.001f);
    }

    @Test
    public void too_small_or_empty_mask_gives_null() {
        assertNull(PersonCropper.prepare(solid(RED), W, H, maskWith(), 150f, 200f));
        assertNull(PersonCropper.prepare(solid(RED), W, H, maskWith(new int[]{10, 20, 10, 20}), 15f, 15f));
    }
}
