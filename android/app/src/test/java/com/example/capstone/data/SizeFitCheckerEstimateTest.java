package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** 사이즈 경고가 "추정" 치수일 때 판정 여유를 넓히고 이유에 표시하는지, 사진 모델 값을 믿는지 확인한다. */
public class SizeFitCheckerEstimateTest {

    private Product top() {
        // 이름에 바지류 단어가 없으면 상의 사이즈표(가슴·어깨 비교)를 쓴다
        return new Product(1, "브랜드", "베이직 티셔츠", 10000);
    }

    @Before
    public void setUp() {
        UserBodyProfile.getInstance().clear();
    }

    @After
    public void tearDown() {
        UserBodyProfile.getInstance().clear();
    }

    /** S 사이즈 가슴 권장 82~90cm. 가슴 99cm는 9cm 벗어난다. */
    private void chest(float cm, boolean estimated) {
        UserBodyProfile p = UserBodyProfile.getInstance();
        p.updateFromMeasurement(160f, -1f, cm, -1f, -1f, -1f);
        p.setEstimated(BodyPrior.CHEST, estimated);
    }

    @Test
    public void measured_value_nine_cm_over_warns() {
        chest(99f, false);
        FitResult r = SizeFitChecker.check(top(), "S");
        assertEquals(FitResult.Status.TIGHT, r.getStatus());
        assertTrue(r.getReasons().get(0).startsWith("가슴둘레 99.0cm"));
    }

    @Test
    public void estimated_value_nine_cm_over_does_not_warn() {
        // 추정은 여유가 5cm라서 9cm 벗어나도 초과분이 4cm(<5)라 경고하지 않는다
        chest(99f, true);
        assertEquals(FitResult.Status.FIT, SizeFitChecker.check(top(), "S").getStatus());
    }

    @Test
    public void estimated_value_far_over_warns_and_is_marked() {
        chest(103f, true);   // 13cm 벗어남 → 초과 8cm
        FitResult r = SizeFitChecker.check(top(), "S");
        assertEquals(FitResult.Status.TIGHT, r.getStatus());
        assertTrue(r.getReasons().get(0).startsWith("가슴둘레(추정) 103.0cm"));
    }

    @Test
    public void new_measurement_clears_estimated_flag() {
        chest(99f, true);
        assertTrue(UserBodyProfile.getInstance().isEstimated(BodyPrior.CHEST));
        UserBodyProfile.getInstance().updateFromMeasurement(160f, -1f, 95f, -1f, -1f, -1f);
        assertFalse(UserBodyProfile.getInstance().isEstimated(BodyPrior.CHEST));
    }

    @Test
    public void recommended_size_is_the_closest_even_when_estimated_tolerance_ties() {
        // 가슴 101.4cm: M(90~98)은 3.4cm 벗어나고 L(98~106)은 딱 맞는다. 추정 여유(5cm) 안에서는 둘 다 0점이지만 L이 가까우니 L을 추천해야 한다
        chest(101.4f, true);
        assertEquals("L", SizeFitChecker.recommendSize(SizeChart.CATEGORY_TOP, UserBodyProfile.getInstance()));
        // 측정값이어도 같다
        chest(101.4f, false);
        assertEquals("L", SizeFitChecker.recommendSize(SizeChart.CATEGORY_TOP, UserBodyProfile.getInstance()));
    }

    @Test
    public void no_profile_means_no_check() {
        assertEquals(FitResult.Status.NO_PROFILE, SizeFitChecker.check(top(), "S").getStatus());
    }

    @Test
    public void trusted_model_value_is_kept_even_if_far_from_baseline() {
        BodyPrior.Result prior = BodyPrior.estimate(160f, 55f, 25f);   // 가슴 기준선 84.1
        boolean[] est = new boolean[4];
        // 사진 모델이 기준선보다 10cm 큰 값(기준선 허용 6.0cm 초과)을 냈다: 믿는 값이면 그대로
        float[] out = BodyPrior.reconcile(prior, new float[]{94.1f, 71f, 92f, -1f}, est,
                new boolean[]{true, true, true, false});
        assertEquals(94.1f, out[BodyPrior.CHEST], 0.001f);
        assertFalse(est[BodyPrior.CHEST]);
        assertTrue(est[BodyPrior.THIGH]);     // 허벅지는 값이 없어 기준선
        // 믿지 않는 값이면 기준선으로 바꾼다
        float[] out2 = BodyPrior.reconcile(prior, new float[]{94.1f, 71f, 92f, -1f}, est);
        assertEquals(prior.values[BodyPrior.CHEST], out2[BodyPrior.CHEST], 0.001f);
    }

    @Test
    public void absurd_model_value_is_replaced() {
        BodyPrior.Result prior = BodyPrior.estimate(160f, 55f, 25f);
        boolean[] est = new boolean[4];
        float[] out = BodyPrior.reconcile(prior, new float[]{prior.values[0] + 20f, 71f, 92f, 55f}, est,
                new boolean[]{true, true, true, false});
        assertEquals(prior.values[BodyPrior.CHEST], out[BodyPrior.CHEST], 0.001f);
        assertTrue(est[BodyPrior.CHEST]);
    }
}
