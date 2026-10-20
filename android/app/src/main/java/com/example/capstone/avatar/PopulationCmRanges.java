package com.example.capstone.avatar;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * AI Hub 한국인 전신 치수(성인) 집계 분위로 슬라이더 참고 범위 제공.
 * 원본 개인 데이터는 포함하지 않음 (p5/p50/p95 등만).
 */
public final class PopulationCmRanges {
    private static final String TAG = "PopulationCmRanges";
    private static final String ASSET = "models/population_cm_ranges.json";

    public static final class Range {
        public final float minCm;
        public final float maxCm;
        public final float medianCm;
        public final int n;

        Range(float minCm, float maxCm, float medianCm, int n) {
            this.minCm = minCm;
            this.maxCm = maxCm;
            this.medianCm = medianCm;
            this.n = n;
        }
    }

    private final Map<String, Range> overallF = new HashMap<>();
    private final Map<Integer, Map<String, Range>> byHeightF = new HashMap<>();
    private boolean loaded;

    public boolean load(Context context) {
        loaded = false;
        overallF.clear();
        byHeightF.clear();
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONObject bySex = root.getJSONObject("bySex");
            if (!bySex.has("F")) {
                return false;
            }
            JSONObject female = bySex.getJSONObject("F");
            JSONObject overall = female.getJSONObject("overall");
            Iterator<String> keys = overall.keys();
            while (keys.hasNext()) {
                String part = keys.next();
                overallF.put(part, readRange(overall.getJSONObject(part)));
            }
            if (female.has("byHeight")) {
                JSONObject byH = female.getJSONObject("byHeight");
                Iterator<String> hk = byH.keys();
                while (hk.hasNext()) {
                    String hs = hk.next();
                    int h = Integer.parseInt(hs);
                    JSONObject block = byH.getJSONObject(hs);
                    JSONObject parts = block.getJSONObject("parts");
                    Map<String, Range> map = new HashMap<>();
                    Iterator<String> pk = parts.keys();
                    while (pk.hasNext()) {
                        String part = pk.next();
                        map.put(part, readRange(parts.getJSONObject(part)));
                    }
                    byHeightF.put(h, map);
                }
            }
            loaded = true;
            Log.i(TAG, "loaded overallParts=" + overallF.size()
                    + " heightBins=" + byHeightF.size());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            return false;
        }
    }

    private static Range readRange(JSONObject o) throws org.json.JSONException {
        float min = (float) o.getDouble("minCm");
        float max = (float) o.getDouble("maxCm");
        float med = (float) o.optDouble("medianCm", 0.5 * (min + max));
        int n = o.optInt("n", 0);
        return new Range(min, max, med, n);
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** 키에 가장 가까운 여성 분위로 참고 범위. 없으면 overall. */
    public Range rangeFor(String partName, float heightCm) {
        if (!loaded || partName == null) {
            return null;
        }
        int bestBin = -1;
        float bestD = Float.POSITIVE_INFINITY;
        for (int h : byHeightF.keySet()) {
            float d = Math.abs(h - heightCm);
            if (d < bestD) {
                bestD = d;
                bestBin = h;
            }
        }
        if (bestBin >= 0 && bestD <= 5f) {
            Map<String, Range> map = byHeightF.get(bestBin);
            if (map != null && map.containsKey(partName)) {
                return map.get(partName);
            }
        }
        return overallF.get(partName);
    }
}