package com.example.capstone.avatar;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Maps app logical morph names (BodyPart / BODY_SLIM / BODY_HEAVY)
 * to glTF morph target names on the loaded avatar.
 */
public final class MorphTargetMap {
    private static final String TAG = "MorphTargetMap";
    public static final String ASSET_PATH = "models/morph_target_map.json";

    private final Map<String, String> logicalToGltf = new HashMap<>();
    private boolean loaded;

    public boolean load(Context context) {
        logicalToGltf.clear();
        loaded = false;
        try (InputStream in = context.getAssets().open(ASSET_PATH);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONObject targets = root.getJSONObject("targets");
            Iterator<String> keys = targets.keys();
            while (keys.hasNext()) {
                String logical = keys.next();
                String gltf = targets.optString(logical, logical);
                if (gltf == null || gltf.isEmpty()) {
                    gltf = logical;
                }
                logicalToGltf.put(logical, gltf);
            }
            loaded = !logicalToGltf.isEmpty();
            ensureDefaults();
            Log.i(TAG, "loaded mappings=" + logicalToGltf.size());
            return loaded;
        } catch (Exception e) {
            Log.e(TAG, "load failed; using identity names", e);
            ensureDefaults();
            loaded = !logicalToGltf.isEmpty();
            return loaded;
        }
    }

    /** Guarantee every app logical key has a glTF name (identity if missing). */
    private void ensureDefaults() {
        String[] keys = {
                "NECK", "NECK_LENGTH", "SHOULDER", "CHEST", "BACK_LENGTH",
                "WAIST", "UPPER_ARM", "UPPER_ARM_LENGTH", "FOREARM", "FOREARM_LENGTH",
                "PELVIS",                 "HIP", "RISE", "THIGH", "THIGH_LENGTH", "CALF", "CALF_LENGTH",
                "MINCUP", "AVERAGECUP", "MAXCUP",
                "BODY_SLIM", "BODY_HEAVY"
        };
        for (String k : keys) {
            if (!logicalToGltf.containsKey(k)) {
                logicalToGltf.put(k, k);
            }
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** glTF morph name for a logical key; falls back to the logical name. */
    public String gltfName(String logical) {
        if (logical == null) {
            return null;
        }
        String mapped = logicalToGltf.get(logical);
        return mapped != null ? mapped : logical;
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(logicalToGltf);
    }

    /**
     * Build logical -> index from a mesh's morph target name list.
     * Missing targets get -1.
     */
    public Map<String, Integer> resolveIndices(String[] meshMorphNames) {
        Map<String, Integer> byGltf = new HashMap<>();
        if (meshMorphNames != null) {
            for (int i = 0; i < meshMorphNames.length; i++) {
                String n = meshMorphNames[i];
                if (n == null) {
                    continue;
                }
                byGltf.put(n, i);
                byGltf.put(n.toUpperCase(Locale.US), i);
            }
        }
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, String> e : logicalToGltf.entrySet()) {
            String gltf = e.getValue();
            Integer idx = byGltf.get(gltf);
            if (idx == null) {
                idx = byGltf.get(gltf.toUpperCase(Locale.US));
            }
            if (idx == null) {
                idx = byGltf.get(e.getKey());
            }
            out.put(e.getKey(), idx != null ? idx : -1);
        }
        // Identity fallback for keys not in JSON
        if (meshMorphNames != null) {
            for (int i = 0; i < meshMorphNames.length; i++) {
                String n = meshMorphNames[i];
                if (n != null && !out.containsKey(n)) {
                    out.put(n, i);
                }
            }
        }
        return out;
    }
}
