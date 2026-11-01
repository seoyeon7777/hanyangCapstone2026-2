package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 키·몸무게·나이 기준선. 기대값은 training/README.md 9장의 계수로 손계산한 값이다. */
public class BodyPriorTest {

    private static final float EPS = 0.01f;

    @Test
    public void adult_160cm_55kg_25years() {
        BodyPrior.Result r = BodyPrior.estimate(160f, 55f, 25f);
        assertNotNull(r);
        assertEquals(84.1034f, r.values[BodyPrior.CHEST], EPS);
        assertEquals(71.1382f, r.values[BodyPrior.WAIST], EPS);
        assertEquals(92.4430f, r.values[BodyPrior.HIP], EPS);
        assertEquals(55.1416f, r.values[BodyPrior.THIGH], EPS);
        assertEquals(2.4f, r.mae[BodyPrior.CHEST], 0.001f);
        assertFalse(r.reference);
    }

    @Test
    public void teen_uses_its_own_coefficients() {
        // 13~18세: 67.3808 - 0.1832*160 + 0.7401*50 + 0.26*16 = 79.2338
        BodyPrior.Result r = BodyPrior.estimate(160f, 50f, 16f);
        assertNotNull(r);
        assertEquals(79.2338f, r.values[BodyPrior.CHEST], EPS);
        assertEquals(2.41f, r.mae[BodyPrior.CHEST], 0.001f);
    }

    @Test
    public void child_uses_its_own_coefficients() {
        // 10~12세: 50.5908 - 0.0976*145 + 0.8331*38 + 0.1808*11 = 70.0854
        BodyPrior.Result r = BodyPrior.estimate(145f, 38f, 11f);
        assertNotNull(r);
        assertEquals(70.0854f, r.values[BodyPrior.CHEST], EPS);
    }

    @Test
    public void seventies_have_larger_waist_error_and_80_plus_is_reference() {
        assertEquals(4.3f, BodyPrior.estimate(155f, 55f, 72f).mae[BodyPrior.WAIST], 0.001f);
        BodyPrior.Result old = BodyPrior.estimate(155f, 55f, 85f);
        BodyPrior.Result seventyNine = BodyPrior.estimate(155f, 55f, 79f);
        assertTrue(old.reference);
        assertEquals(seventyNine.values[BodyPrior.WAIST], old.values[BodyPrior.WAIST], 0.001f);
    }

    @Test
    public void out_of_range_input_gives_no_estimate() {
        assertNull(BodyPrior.estimate(160f, 55f, 9f));      // 나이 범위 밖
        assertNull(BodyPrior.estimate(160f, 55f, 100f));
        assertNull(BodyPrior.estimate(160f, 20f, 25f));     // 몸무게 범위 밖
        assertNull(BodyPrior.estimate(100f, 55f, 25f));     // 키 범위 밖
        assertNull(BodyPrior.estimate(-1f, 55f, 25f));      // 입력 없음
        assertNull(BodyPrior.estimate(160f, -1f, 25f));
        assertNull(BodyPrior.estimate(160f, 55f, -1f));
    }

    @Test
    public void implausible_measurement_is_replaced_by_estimate() {
        BodyPrior.Result r = BodyPrior.estimate(160f, 55f, 25f);   // 가슴 84.1, 허용 오차 max(5, 2.5*2.4)=6.0
        assertFalse(BodyPrior.isImplausible(r, "CHEST", 90.0f));
        assertTrue(BodyPrior.isImplausible(r, "CHEST", 90.2f));
        assertFalse(BodyPrior.isImplausible(null, "CHEST", 200f));
        assertFalse(BodyPrior.isImplausible(r, "SHOULDER", 200f));

        boolean[] est = new boolean[4];
        // 가슴: 옷 때문에 크게 나온 값, 허리: 그럴듯한 값, 엉덩이: 없음, 허벅지: 그럴듯한 값
        float[] out = BodyPrior.reconcile(r, new float[]{110f, 72f, -1f, 55f}, est);
        assertEquals(r.values[BodyPrior.CHEST], out[BodyPrior.CHEST], 0.001f);
        assertEquals(72f, out[BodyPrior.WAIST], 0.001f);
        assertEquals(r.values[BodyPrior.HIP], out[BodyPrior.HIP], 0.001f);
        assertEquals(55f, out[BodyPrior.THIGH], 0.001f);
        assertTrue(est[BodyPrior.CHEST]);
        assertFalse(est[BodyPrior.WAIST]);
        assertTrue(est[BodyPrior.HIP]);
        assertFalse(est[BodyPrior.THIGH]);
    }

    @Test
    public void reconcile_without_prior_keeps_measurements() {
        boolean[] est = new boolean[4];
        float[] out = BodyPrior.reconcile(null, new float[]{110f, -1f, 90f, 55f}, est);
        assertEquals(110f, out[BodyPrior.CHEST], 0.001f);
        assertEquals(-1f, out[BodyPrior.WAIST], 0.001f);
        assertFalse(est[BodyPrior.CHEST]);
        assertFalse(est[BodyPrior.WAIST]);
    }
}
