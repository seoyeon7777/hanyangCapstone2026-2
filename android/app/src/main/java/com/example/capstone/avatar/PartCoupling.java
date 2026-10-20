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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 부위 슬라이더 연동 비율.
 * AI Hub 한국인 전신 치수(성인, 여성) 키-통제 회귀 + 감쇠.
 */
public final class PartCoupling {
    private static final String TAG = "PartCoupling";
    private static final String ASSET = "models/part_coupling.json";

    public static final class Link {
        public final String target;
        public final float scale;

        Link(String target, float scale) {
            this.target = target;
            this.scale = scale;
        }
    }

    private final Map<String, List<Link>> byDriver = new HashMap<>();
    private boolean loaded;

    public boolean load(Context context) {
        loaded = false;
        byDriver.clear();
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONObject couplings = root.getJSONObject("couplings");
            JSONArray names = couplings.names();
            if (names == null) {
                return false;
            }
            for (int i = 0; i < names.length(); i++) {
                String driver = names.getString(i);
                JSONArray arr = couplings.getJSONArray(driver);
                List<Link> links = new ArrayList<>();
                for (int j = 0; j < arr.length(); j++) {
                    JSONObject o = arr.getJSONObject(j);
                    links.add(new Link(o.getString("target"), (float) o.getDouble("scale")));
                }
                byDriver.put(driver, links);
            }
            loaded = true;
            Log.i(TAG, "loaded drivers=" + byDriver.size()
                    + " credit=" + root.optString("credit"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            return false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<Link> linksFor(String driverName) {
        List<Link> list = byDriver.get(driverName);
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }
}
