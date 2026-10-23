package com.example.capstone.avatar;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * morph_cm_lut.json → cm ↔ morph weight.
 *
 * 슬라이더 단위는 항상 메쉬 줄자 cm 이다 (키 스케일).
 * 인구 보정 metric_m 은 쓰지 않는다. shape key 는 바꾸지 않는다.
 *
 *   mesh_cm(w) = metric_m_mesh(w) * 100 * (heightCm / nativeHeightCm)
 *
 * 슬라이더 범위는 그 키가 실제로 움직이는 구간만 연다.
 * 반응이 작은 부위는 범위가 좁고, 큰 부위는 넓다.
 */
public final class MorphCmLut {
    private static final String TAG = "MorphCmLut";
    private static final String ASSET = "models/morph_cm_lut.json";
    private static final String CONTRACT = "models/mesh_cm_contract.json";
    /** 이보다 작은 메쉬 변화는 슬라이더로 조절하지 않는다 (약 1.5mm). */
    private static final float MIN_USABLE_RATIO = 0.0015f;

    public static final class Sample {
        public final float w;
        public final float metricM;
        public final float ratio;

        Sample(float w, float metricM, float ratio) {
            this.w = w;
            this.metricM = metricM;
            this.ratio = ratio;
        }
    }

    public static final class PartCurve {
        public final List<Sample> samples = new ArrayList<>();
        /** w=0에서 바깥으로 걷되 스파이크/비단조를 자른 안전 샘플. */
        public final List<Sample> safeSamples = new ArrayList<>();
        public float metric0M = 1f;
        public float safeMinMetric = Float.POSITIVE_INFINITY;
        public float safeMaxMetric = Float.NEGATIVE_INFINITY;
        public float safeMinRatio = 1f;
        public float safeMaxRatio = 1f;
        public float safeMinW = 0f;
        public float safeMaxW = 0f;
        public boolean usable;
        public boolean absolute;
        public float ratioSpan;
        public String meaning = "";
        /** realMedian / storedCm. Kept at 1 — display is mesh tape. */
        public float realScale = 1f;
        public boolean realCalibrated;
        /** Samples are absolute mesh tape cm. */
        public boolean realCmStored;
        /** Multiply SizeKorea/camera cm → mesh tape cm (mesh0/popMedian). */
        public float sizeKoreaToMesh = 1f;
        public float populationMedianCm = Float.NaN;
        public String populationColumn = "";
    }

    private final Map<String, PartCurve> parts = new HashMap<>();
    private float nativeHeightCm = 170f;
    private int version;
    private String method = "";
    private boolean loaded;

    public boolean load(Context context) {
        loaded = false;
        parts.clear();
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            version = root.optInt("version", 0);
            method = root.optString("method", "");
            if (root.has("nativeHeightCm")) {
                nativeHeightCm = (float) root.getDouble("nativeHeightCm");
            }
            JSONObject partsJson = root.getJSONObject("parts");
            JSONArray names = partsJson.names();
            if (names == null) {
                return false;
            }
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                JSONObject part = partsJson.getJSONObject(name);
                JSONArray samples = part.getJSONArray("samples");
                PartCurve curve = new PartCurve();
                curve.realCmStored = "real_cm".equals(part.optString("unit"));
                // Prefer meshMetric0 — display/morph use the same mesh tape.
                if (part.has("meshMetric0_m")) {
                    curve.metric0M = (float) part.getDouble("meshMetric0_m");
                } else if (part.has("realCm0")) {
                    curve.metric0M = (float) part.getDouble("realCm0") / 100f;
                } else if (part.has("metric0_m")) {
                    curve.metric0M = (float) part.getDouble("metric0_m");
                }
                curve.sizeKoreaToMesh = (float) part.optDouble("sizeKoreaToMesh", 1.0);
                if (curve.sizeKoreaToMesh < 1e-4f) {
                    curve.sizeKoreaToMesh = 1f;
                }
                if (part.has("populationMedianCm")) {
                    curve.populationMedianCm = (float) part.getDouble("populationMedianCm");
                }
                curve.populationColumn = part.optString("populationColumn", "");
                for (int s = 0; s < samples.length(); s++) {
                    JSONObject o = samples.getJSONObject(s);
                    float w = (float) o.getDouble("w");
                    float ratio = (float) o.optDouble("ratio", 1.0);
                    float metricM;
                    // Prefer raw mesh tape fields so scaled "cm" cannot lie.
                    if (o.has("metric_m_mesh")) {
                        metricM = (float) o.getDouble("metric_m_mesh");
                        curve.absolute = true;
                    } else if (o.has("cm_mesh_raw")) {
                        metricM = (float) o.getDouble("cm_mesh_raw") / 100f;
                        curve.absolute = true;
                    } else if (o.has("cm_mesh")) {
                        metricM = (float) o.getDouble("cm_mesh") / 100f;
                        curve.absolute = true;
                    } else if (curve.realCmStored && o.has("cm")) {
                        metricM = (float) o.getDouble("cm") / 100f;
                        curve.absolute = true;
                    } else if (o.has("metric_m")) {
                        metricM = (float) o.getDouble("metric_m");
                        curve.absolute = true;
                    } else {
                        metricM = ratio;
                    }
                    if (Math.abs(w) < 1e-4f) {
                        curve.metric0M = metricM;
                    }
                    curve.samples.add(new Sample(w, metricM, ratio));
                }
                Collections.sort(curve.samples, Comparator.comparingDouble(a -> a.w));
                // ratio는 항상 w=0 metric 대비로 통일
                float m0 = curve.metric0M > 1e-8f ? curve.metric0M : 1f;
                List<Sample> normalized = new ArrayList<>();
                for (Sample s : curve.samples) {
                    normalized.add(new Sample(s.w, s.metricM, s.metricM / m0));
                }
                curve.samples.clear();
                curve.samples.addAll(normalized);
                buildSafeEnvelope(name, curve);
                curve.ratioSpan = curve.safeMaxRatio - curve.safeMinRatio;
                curve.usable = curve.absolute
                        && curve.safeSamples.size() >= 3
                        && curve.ratioSpan >= MIN_USABLE_RATIO;
                if (curve.realCmStored) {
                    curve.realCalibrated = true;
                }
                if (!curve.usable) {
                    Log.w(TAG, "LUT unusable for " + name
                            + " safeSamples=" + curve.safeSamples.size()
                            + " ratioSpan=" + curve.ratioSpan
                            + " → no invented cm range");
                } else if (curve.ratioSpan < 0.03f) {
                    Log.i(TAG, "weak mesh response " + name
                            + " span%=" + (curve.ratioSpan * 100f));
                }
                putWithAliases(name, curve);
            }
            loadContract(context);
            loaded = true;
            Log.i(TAG, "loaded v" + version + " parts=" + parts.size()
                    + " usable=" + countUsable()
                    + " nativeHeightCm=" + nativeHeightCm
                    + " method=" + method);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            return false;
        }
    }

    private void putWithAliases(String name, PartCurve curve) {
        parts.put(name, curve);
    }

    private void loadContract(Context context) {
        try (InputStream in = context.getAssets().open(CONTRACT);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONObject meanings = root.optJSONObject("parts");
            if (meanings == null) {
                return;
            }
            JSONArray names = meanings.names();
            if (names == null) {
                return;
            }
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                PartCurve curve = parts.get(name);
                if (curve != null) {
                    curve.meaning = meanings.optString(name, "");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "mesh cm contract missing", e);
        }
    }

    /**
     * Honest mode: do not rescale display to population medians.
     * Screen cm stays mesh tape; SizeKorea inputs convert via {@link #sizeKoreaToMeshCm}.
     */
    public void applyRealTape(PopulationCmRanges pop, float heightCm) {
        for (PartCurve curve : parts.values()) {
            curve.realScale = 1f;
            // Mesh tape is the real measurement of this model.
            curve.realCalibrated = curve.absolute && curve.metric0M > 1e-8f;
        }
    }

    /**
     * Camera / AI Hub SizeKorea cm → mesh tape cm (same unit the slider shows).
     */
    public float sizeKoreaToMeshCm(String partName, float sizeKoreaCm) {
        if (Float.isNaN(sizeKoreaCm) || sizeKoreaCm <= 0f) {
            return sizeKoreaCm;
        }
        PartCurve c = parts.get(partName);
        if (c == null || c.sizeKoreaToMesh <= 1e-6f) {
            return sizeKoreaCm;
        }
        return sizeKoreaCm * c.sizeKoreaToMesh;
    }

    public float meshToSizeKoreaCm(String partName, float meshCm) {
        PartCurve c = parts.get(partName);
        if (c == null || c.sizeKoreaToMesh <= 1e-6f) {
            return meshCm;
        }
        return meshCm / c.sizeKoreaToMesh;
    }

    public boolean isRealCalibrated(String partName) {
        PartCurve c = parts.get(partName);
        return c != null && c.realCalibrated;
    }

    /** 슬라이더에 찍히는 숫자의 뜻. */
    public String partHint(String partName, String label) {
        PartCurve c = parts.get(partName);
        String meaning = (c != null && c.meaning != null && !c.meaning.isEmpty())
                ? c.meaning : "메쉬 줄자";
        String col = (c != null && c.populationColumn != null && !c.populationColumn.isEmpty())
                ? c.populationColumn : partName;
        return label + ": " + meaning + " · 화면=모델 줄자cm (" + col + " 입력→환산), "
                + responseLabel(partName);
    }

    public String responseLabel(String partName) {
        PartCurve c = parts.get(partName);
        if (c == null || !c.usable) {
            return "거의 안 변함";
        }
        if (c.ratioSpan >= 0.15f) {
            return "변화 큼";
        }
        if (c.ratioSpan >= 0.05f) {
            return "변화 보통";
        }
        if (c.ratioSpan >= 0.03f) {
            return "변화 작음";
        }
        return "거의 안 변함";
    }

    /**
     * morph |w| 상한 — 줄자 폭보다 “안 깨지는 모습” 우선.
     * |w|≈1 근처에서 허벅지·가슴 등이 오목하게 붕괴하므로 슬라이더에서 제외한다.
     * 기본 모습(w=0)은 항상 범위 중앙에 남는다.
     */
    /** Blender shape key safe |w| — arm lengths collapse if driven to ±1. */
    private static float safeAbsW(String name) {
        if ("UPPER_ARM_LENGTH".equals(name) || "FOREARM_LENGTH".equals(name)) {
            return 1.0f;
        }
        if ("THIGH_LENGTH".equals(name) || "CALF_LENGTH".equals(name)) {
            return 0.85f;
        }
        // HIP |w|≈1 → pointy buttocks; allow most of the range.
        if ("HIP".equals(name)) {
            return 0.78f;
        }
        if ("RISE".equals(name)) {
            return 0.85f;
        }
        // Full native waist span — earlier 0.72 made the slider feel dead.
        if ("WAIST".equals(name)) {
            return 1.0f;
        }
        // Need visible girth travel; soft silhouette is bake-side, not |w| crush.
        if ("THIGH".equals(name) || "CALF".equals(name)) {
            return 0.95f;
        }
        if ("BACK_LENGTH".equals(name)) {
            return 0.70f;
        }
        if ("PELVIS".equals(name)) {
            return 0.70f;
        }
        return 0.95f;
    }

    /**
     * w=0에서 ±로 확장. 한 스텝 스파이크·방향 반전에서 끊는다.
     * 메쉬가 접히는 끝단과 줄자 튀김을 슬라이더에서 제외한다.
     */
    private static void buildSafeEnvelope(String name, PartCurve curve) {
        List<Sample> sorted = curve.samples;
        int i0 = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (Math.abs(sorted.get(i).w) < 1e-4f) {
                i0 = i;
                break;
            }
        }
        if (i0 < 0) {
            i0 = sorted.size() / 2;
        }
        float wLim = safeAbsW(name);
        float m0 = Math.max(Math.abs(sorted.get(i0).metricM), 1e-4f);
        List<Sample> safe = new ArrayList<>();
        safe.add(sorted.get(i0));

        expandSide(sorted, i0, +1, wLim, m0, safe);
        expandSide(sorted, i0, -1, wLim, m0, safe);
        Collections.sort(safe, Comparator.comparingDouble(a -> a.w));

        curve.safeSamples.clear();
        curve.safeSamples.addAll(safe);
        curve.safeMinMetric = Float.POSITIVE_INFINITY;
        curve.safeMaxMetric = Float.NEGATIVE_INFINITY;
        curve.safeMinRatio = Float.POSITIVE_INFINITY;
        curve.safeMaxRatio = Float.NEGATIVE_INFINITY;
        curve.safeMinW = 0f;
        curve.safeMaxW = 0f;
        for (Sample s : safe) {
            curve.safeMinMetric = Math.min(curve.safeMinMetric, s.metricM);
            curve.safeMaxMetric = Math.max(curve.safeMaxMetric, s.metricM);
            curve.safeMinRatio = Math.min(curve.safeMinRatio, s.ratio);
            curve.safeMaxRatio = Math.max(curve.safeMaxRatio, s.ratio);
            curve.safeMinW = Math.min(curve.safeMinW, s.w);
            curve.safeMaxW = Math.max(curve.safeMaxW, s.w);
        }
    }

    private static void expandSide(List<Sample> sorted, int i0, int dir,
                                   float wLim, float m0, List<Sample> safe) {
        float prev = sorted.get(i0).metricM;
        int direction = 0;
        for (int i = i0 + dir; i >= 0 && i < sorted.size(); i += dir) {
            Sample s = sorted.get(i);
            if (Math.abs(s.w) > wLim + 1e-4f) {
                break;
            }
            float d = s.metricM - prev;
            float step = Math.abs(d) / m0;
            if (step > 0.45f) {
                break;
            }
            int sign = d > 1e-6f ? 1 : (d < -1e-6f ? -1 : 0);
            if (direction == 0 && sign != 0) {
                direction = sign;
            } else if (sign != 0 && sign != direction) {
                break;
            }
            safe.add(s);
            prev = s.metricM;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean hasUsable(String partName) {
        PartCurve c = parts.get(partName);
        return c != null && c.usable;
    }

    public int getVersion() {
        return version;
    }

    public int countUsable() {
        int n = 0;
        for (PartCurve c : parts.values()) {
            if (c.usable) {
                n++;
            }
        }
        return n;
    }

    /**
     * 인구 median cm 기준 비율 → morph weight (mask-tape proxy 부위용).
     * mesh 절대 cm(예: HIP lower-butt ~18cm) 대신 p50 줄자(예: ~93cm)로 슬라이더 표시.
     */
    public float weightForPopulationRatio(String partName, float targetRatio) {
        PartCurve curve = parts.get(partName);
        if (curve == null || !curve.usable || curve.safeSamples.isEmpty()) {
            return fallbackWeight(targetRatio);
        }
        if (Math.abs(targetRatio - 1f) < 0.004f) {
            return 0f;
        }
        float w = weightForMetric(curve.safeSamples, targetRatio, false);
        return clamp(w, curve.safeMinW, curve.safeMaxW);
    }

    public float getNativeHeightCm() {
        return nativeHeightCm;
    }

    public float baselineCm(String partName, float heightCm) {
        // 키에 맞춘 메쉬 줄자 절대 cm (w=0)
        PartCurve c = parts.get(partName);
        if (c == null || !c.absolute || c.metric0M <= 1e-8f
                || nativeHeightCm <= 1e-3f || heightCm <= 1e-3f) {
            return Float.NaN;
        }
        float heightScale = c.realCmStored ? 1f : (heightCm / nativeHeightCm);
        return c.metric0M * 100f * heightScale * c.realScale;
    }

    public float safeMinRatio(String partName) {
        PartCurve c = parts.get(partName);
        if (c == null || !c.usable) {
            return Float.NaN;
        }
        return c.safeMinRatio;
    }

    public float safeMaxRatio(String partName) {
        PartCurve c = parts.get(partName);
        if (c == null || !c.usable) {
            return Float.NaN;
        }
        return c.safeMaxRatio;
    }

    /** 슬라이더 최소: 안전 morph 구간의 절대 cm (키 스케일). */
    public float minAbsCm(String partName, float heightCm) {
        PartCurve c = parts.get(partName);
        if (c == null || !c.usable || !c.absolute
                || nativeHeightCm <= 1e-3f || heightCm <= 1e-3f) {
            return Float.NaN;
        }
        float heightScale = c.realCmStored ? 1f : (heightCm / nativeHeightCm);
        return c.safeMinMetric * 100f * heightScale * c.realScale;
    }

    /** 슬라이더 최대: 안전 morph 구간의 절대 cm (키 스케일). */
    public float maxAbsCm(String partName, float heightCm) {
        PartCurve c = parts.get(partName);
        if (c == null || !c.usable || !c.absolute
                || nativeHeightCm <= 1e-3f || heightCm <= 1e-3f) {
            return Float.NaN;
        }
        float heightScale = c.realCmStored ? 1f : (heightCm / nativeHeightCm);
        return c.safeMaxMetric * 100f * heightScale * c.realScale;
    }

    /**
     * @deprecated 비율×인체추정 — {@link #minAbsCm(String, float)} 사용
     */
    public float minCm(String partName, float anthroBaseCm) {
        float r = safeMinRatio(partName);
        if (Float.isNaN(r) || anthroBaseCm <= 1e-3f) {
            return Float.NaN;
        }
        return anthroBaseCm * r;
    }

    /** @deprecated 비율×인체추정 — {@link #maxAbsCm(String, float)} 사용 */
    public float maxCm(String partName, float anthroBaseCm) {
        float r = safeMaxRatio(partName);
        if (Float.isNaN(r) || anthroBaseCm <= 1e-3f) {
            return Float.NaN;
        }
        return anthroBaseCm * r;
    }

    /** @deprecated height 스케일 절대 cm — {@link #minAbsCm(String, float)} 사용 */
    public float minCm(String partName, float heightCm, boolean unusedAbsolute) {
        return minAbsCm(partName, heightCm);
    }

    /**
     * userCm(키 스케일 절대 cm) → morph weight.
     * target_metric_m = (userCm/100) * (nativeHeightCm/heightCm)
     */
    public float weightForCm(String partName, float userCm, float anthroBaseCm,
                             float heightCm) {
        PartCurve curve = parts.get(partName);
        if (curve == null || !curve.usable) {
            if (anthroBaseCm <= 1e-3f) {
                return 0f;
            }
            return fallbackWeight(userCm / anthroBaseCm);
        }
        // Prefer 1:1 absolute mesh tape when available.
        if (curve.absolute && nativeHeightCm > 1e-3f && heightCm > 1e-3f) {
            float scale = curve.realScale > 1e-6f ? curve.realScale : 1f;
            float heightBack = curve.realCmStored ? 1f : (nativeHeightCm / heightCm);
            float targetM = (userCm / scale / 100f) * heightBack;
            float m0 = curve.metric0M;
            if (m0 > 1e-8f && Math.abs(targetM - m0) / m0 < 0.004f) {
                return 0f;
            }
            float w = weightForMetric(curve.safeSamples, targetM, true);
            return clamp(w, curve.safeMinW, curve.safeMaxW);
        }
        if (anthroBaseCm <= 1e-3f) {
            return 0f;
        }
        float targetRatio = userCm / anthroBaseCm;
        if (Math.abs(targetRatio - 1f) < 0.004f) {
            return 0f;
        }
        float w = weightForMetric(curve.safeSamples, targetRatio, false);
        return clamp(w, curve.safeMinW, curve.safeMaxW);
    }

    /** morph weight에서의 달성 비율 (w=0 대비). */
    public float ratioAt(String partName, float weight) {
        PartCurve curve = parts.get(partName);
        if (curve == null || curve.safeSamples.isEmpty()) {
            return 1f;
        }
        List<Sample> s = curve.safeSamples;
        if (weight <= s.get(0).w) {
            return s.get(0).ratio;
        }
        if (weight >= s.get(s.size() - 1).w) {
            return s.get(s.size() - 1).ratio;
        }
        for (int i = 0; i < s.size() - 1; i++) {
            Sample a = s.get(i);
            Sample b = s.get(i + 1);
            if (weight < a.w || weight > b.w) {
                continue;
            }
            float t = (Math.abs(b.w - a.w) < 1e-9f) ? 0f : (weight - a.w) / (b.w - a.w);
            return a.ratio + t * (b.ratio - a.ratio);
        }
        return 1f;
    }

    public float refineWeight(String partName, float userCm, float anthroBaseCm,
                              float heightCm) {
        return weightForCm(partName, userCm, anthroBaseCm, heightCm);
    }

    public float weightForCm(String partName, float userCm, float anthroBaseCm) {
        return weightForCm(partName, userCm, anthroBaseCm, nativeHeightCm);
    }

    public float refineWeight(String partName, float userCm, float anthroBaseCm) {
        return refineWeight(partName, userCm, anthroBaseCm, nativeHeightCm);
    }

    private static float weightForMetric(List<Sample> s, float target, boolean absolute) {
        float bestW = 0f;
        float bestErr = Float.POSITIVE_INFINITY;
        boolean foundSeg = false;
        float segAbsW = Float.POSITIVE_INFINITY;

        for (int i = 0; i < s.size() - 1; i++) {
            Sample a = s.get(i);
            Sample b = s.get(i + 1);
            float v0 = absolute ? a.metricM : a.ratio;
            float v1 = absolute ? b.metricM : b.ratio;
            if (target < Math.min(v0, v1) || target > Math.max(v0, v1)) {
                continue;
            }
            float t = (Math.abs(v1 - v0) < 1e-9f) ? 0f : (target - v0) / (v1 - v0);
            float w = a.w + t * (b.w - a.w);
            float midAbs = Math.abs(0.5f * (a.w + b.w));
            if (!foundSeg || midAbs < segAbsW) {
                foundSeg = true;
                segAbsW = midAbs;
                bestW = w;
            }
        }
        if (foundSeg) {
            return bestW;
        }
        for (Sample sample : s) {
            float v = absolute ? sample.metricM : sample.ratio;
            float err = Math.abs(v - target);
            if (err < bestErr) {
                bestErr = err;
                bestW = sample.w;
            }
        }
        return bestW;
    }

    public static float fallbackWeight(float scaleOrRatio) {
        return clamp((scaleOrRatio - 1f) / 0.4f, -1f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "MorphCmLut(loaded=%s, parts=%d, nativeH=%.1f)",
                loaded, parts.size(), nativeHeightCm);
    }
}
