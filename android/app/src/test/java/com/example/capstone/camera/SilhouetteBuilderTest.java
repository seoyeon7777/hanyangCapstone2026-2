package com.example.capstone.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 윤곽에서 몸 덩어리를 고르는 규칙: 관절 중심선이 있으면 배경 물건보다 몸을 고른다. */
public class SilhouetteBuilderTest {

    private static final int W = 100;
    private static final int H = 200;

    private static ByteBuffer mask(Object... rects) {
        ByteBuffer b = ByteBuffer.allocate(W * H * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < W * H; i++) {
            b.putFloat(0f);
        }
        for (Object r : rects) {
            int[] a = (int[]) r;   // x0, x1, y0, y1 (포함)
            for (int y = a[2]; y <= a[3]; y++) {
                for (int x = a[0]; x <= a[1]; x++) {
                    b.putFloat((y * W + x) * 4, 1f);
                }
            }
        }
        b.rewind();
        return b;
    }

    // 몸: x 60~79, 20~180줄. 배경 물건: x 40~49, 0~199줄 (화면 가운데 x=50에 더 가깝다)
    private static final int[] BODY = {60, 79, 20, 180};
    private static final int[] DECOY = {40, 49, 0, 199};

    @Test
    public void withoutAxisPicksTheBlobNearestTheImageCenter() {
        MeasurementSession.Silhouette s = SilhouetteBuilder.build(mask(BODY, DECOY), W, H, null);
        assertEquals(40, s.left[100]);          // 예전 방식: 화면 가운데에 가까운 배경 물건을 고른다
        assertEquals(0, s.top);
    }

    @Test
    public void withAxisPicksTheBodyAndIgnoresTheDecoy() {
        // 관절 중심선 x=0.70, 몸 키 = 화면의 80%
        SilhouetteBuilder.Axis axis = new SilhouetteBuilder.Axis(0.70f, 0.8f);
        MeasurementSession.Silhouette s = SilhouetteBuilder.build(mask(BODY, DECOY), W, H, axis);
        assertEquals(60, s.left[100]);
        assertEquals(79, s.right[100]);
        assertEquals(20, s.top);                // 배경 물건 때문에 위·아래가 늘어나지 않는다
        assertEquals(180, s.bottom);
        assertEquals(20f, s.widthPx(0.5f), 0f);
    }

    @Test
    public void bodyMergedWithObjectIsCutAtTheWindow() {
        // 몸(60~79)에 물건(80~99)이 붙어 있다 → 중심선 70, 몸 키 160줄 → 좌우 ±26줄(44~96)까지만 본다
        int[] merged = {60, 99, 20, 180};
        SilhouetteBuilder.Axis axis = new SilhouetteBuilder.Axis(0.70f, 0.8f);
        MeasurementSession.Silhouette s = SilhouetteBuilder.build(mask(merged), W, H, axis);
        assertEquals(60, s.left[100]);
        assertEquals(96, s.right[100]);         // 96에서 잘림 (창 끝)
    }

    @Test
    public void blobsFarFromTheAxisAreNotTheBody() {
        // 중심선(70)에서 32줄 떨어진 덩어리(x 20~29)만 있고 허용 거리는 0.12 × 160 = 19.2
        SilhouetteBuilder.Axis axis = new SilhouetteBuilder.Axis(0.70f, 0.8f);
        assertNull(SilhouetteBuilder.build(mask(new int[]{20, 29, 20, 180}), W, H, axis));
    }
}
