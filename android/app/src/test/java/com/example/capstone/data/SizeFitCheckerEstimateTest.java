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
        // 이름에 바지류 단어가 없으면 상의 사이즈표(가슴둘레만 비교)를 쓴다
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

    private Product bottom() {
        return new Product(2, "브랜드", "와이드 팬츠", 10000);
    }

    /** S 사이즈 가슴 권장 82.5~87.5cm(KS K 0051 호칭 85 ±2.5). 가슴 99cm는 11.5cm 벗어난다. */
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
    public void estimated_value_seven_cm_over_does_not_warn() {
        // 추정은 여유가 5cm라서 7.5cm 벗어나도 초과분이 2.5cm(<5)라 경고하지 않는다
        chest(95f, true);
        assertEquals(FitResult.Status.FIT, SizeFitChecker.check(top(), "S").getStatus());
    }

    @Test
    public void estimated_value_far_over_warns_and_is_marked() {
        chest(103f, true);   // 15.5cm 벗어남 → 초과 10.5cm
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
        // 가슴 94cm: M(87.5~92.5)은 1.5cm 벗어나고 L(92.5~97.5)은 딱 맞는다. 여유(3cm·추정 5cm) 안에서는 둘 다 0점이지만 L이 가까우니 L을 추천해야 한다
        chest(94f, true);
        assertEquals("L", SizeFitChecker.recommendSize(SizeChart.CATEGORY_TOP, UserBodyProfile.getInstance()));
        // 측정값이어도 같다
        chest(94f, false);
        assertEquals("L", SizeFitChecker.recommendSize(SizeChart.CATEGORY_TOP, UserBodyProfile.getInstance()));
    }

    @Test
    public void top_is_judged_by_chest_only_not_shoulder() {
        // 앱이 재는 어깨너비(관절 사이, 약 31cm)는 옷 사이즈 기준이 아니라서 상의 판정에 쓰지 않는다 (예전에는 40cm 기준이라 항상 경고가 떴다)
        UserBodyProfile p = UserBodyProfile.getInstance();
        p.updateFromMeasurement(160f, 31f, 85f, -1f, -1f, -1f);
        assertEquals(FitResult.Status.FIT, SizeFitChecker.check(top(), "S").getStatus());
        assertEquals(FitResult.Status.FIT, SizeFitChecker.check(top(), "M").getStatus());   // 85는 M(87.5~92.5)에서 2.5cm 벗어나도 여유 3cm 안
    }

    @Test
    public void bottom_is_judged_by_waist_only_not_hip_or_thigh() {
        UserBodyProfile p = UserBodyProfile.getInstance();
        // 허리 75cm = M(72.5~77.5). 엉덩이·허벅지가 커도 캐주얼 하의는 허리로만 사이즈를 정한다 (KS K 0051 표 10)
        p.updateFromMeasurement(160f, -1f, -1f, 75f, 120f, 75f);
        assertEquals(FitResult.Status.FIT, SizeFitChecker.check(bottom(), "M").getStatus());
        assertEquals("M", SizeFitChecker.recommendSize(SizeChart.CATEGORY_BOTTOM, p));
    }

    @Test
    public void bottom_waist_far_over_warns() {
        UserBodyProfile p = UserBodyProfile.getInstance();
        p.updateFromMeasurement(160f, -1f, -1f, 86f, -1f, -1f);    // S(67.5~72.5)보다 13.5cm 크다
        FitResult r = SizeFitChecker.check(bottom(), "S");
        assertEquals(FitResult.Status.TIGHT, r.getStatus());
        assertTrue(r.getReasons().get(0).startsWith("허리둘레 86.0cm"));
    }

    @Test
    public void size_ranges_follow_ks_k_0051_nominal_plus_minus_2_5() {
        SizeChart.SizeSpec s = SizeChart.getSpec(SizeChart.CATEGORY_TOP, "S");
        SizeChart.SizeSpec m = SizeChart.getSpec(SizeChart.CATEGORY_TOP, "M");
        SizeChart.SizeSpec l = SizeChart.getSpec(SizeChart.CATEGORY_TOP, "L");
        assertEquals(82.5f, s.chestMin, 0.001f);
        assertEquals(87.5f, s.chestMax, 0.001f);
        assertEquals(s.chestMax, m.chestMin, 0.001f);     // 호칭 5cm 간격이라 서로 이어진다
        assertEquals(m.chestMax, l.chestMin, 0.001f);
        assertEquals(97.5f, l.chestMax, 0.001f);
        SizeChart.SizeSpec bs = SizeChart.getSpec(SizeChart.CATEGORY_BOTTOM, "S");
        SizeChart.SizeSpec bl = SizeChart.getSpec(SizeChart.CATEGORY_BOTTOM, "L");
        assertEquals(67.5f, bs.waistMin, 0.001f);
        assertEquals(82.5f, bl.waistMax, 0.001f);
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
