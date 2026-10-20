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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SMPL beta + residual hybrid solver.
 *
 * meshTarget ≈ mesh0 + J · beta
 * residualW = clamp((meshTarget - predicted) / residualCap, -1, 1)
 */
public final class SmplHybridSolver {
    private static final String TAG = "SmplHybridSolver";
    public static final String ASSET = "models/smpl_hybrid_solver.json";

    public static final class Result {
        public final float[] betas;
        public final Map<String, Float> residualW = new HashMap<>();
        public final Map<String, Float> lengthScale = new HashMap<>();

        Result(int nBetas) {
            betas = new float[nBetas];
        }
    }

    private final List<String> betaNames = new ArrayList<>();
    private final List<String> girthParts = new ArrayList<>();
    private final List<String> allGirthParts = new ArrayList<>();
    private final Map<String, Float> mesh0Cm = new HashMap<>();
    private final Map<String, Float> residualCapCm = new HashMap<>();
    private final Map<String, String> residualMorph = new HashMap<>();
    private final Map<String, String[]> lengthBones = new HashMap<>();
    /** row-major jacobian[girthIndex][betaIndex] in cm per beta unit */
    private float[][] jacobian = new float[0][0];
    private float betaLimit = 1f;
    private float ridge = 0.85f;
    private float residualMaxAbsW = 0.85f;
    private float lengthScaleMin = 0.85f;
    private float lengthScaleMax = 1.20f;
    private float nativeHeightCm = 165f;
    private boolean loaded;

    public boolean load(Context context) {
        loaded = false;
            betaNames.clear();
        girthParts.clear();
        allGirthParts.clear();
        mesh0Cm.clear();
        residualCapCm.clear();
        residualMorph.clear();
        lengthBones.clear();
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            nativeHeightCm = (float) root.optDouble("nativeHeightCm", 165.0);
            betaLimit = (float) root.optDouble("betaLimit", 1.0);
            ridge = (float) root.optDouble("ridge", 0.85);
            residualMaxAbsW = (float) root.optDouble("residualMaxAbsW", 0.85);
            lengthScaleMin = (float) root.optDouble("lengthScaleMin", 0.85);
            lengthScaleMax = (float) root.optDouble("lengthScaleMax", 1.20);

            JSONArray bn = root.getJSONArray("betaNames");
            for (int i = 0; i < bn.length(); i++) {
                betaNames.add(bn.getString(i));
            }
            JSONArray gp = root.getJSONArray("girthParts");
            for (int i = 0; i < gp.length(); i++) {
                girthParts.add(gp.getString(i));
            }
            if (root.has("allGirthParts")) {
                JSONArray ag = root.getJSONArray("allGirthParts");
                for (int i = 0; i < ag.length(); i++) {
                    allGirthParts.add(ag.getString(i));
                }
            } else {
                allGirthParts.addAll(girthParts);
            }

            JSONObject m0 = root.getJSONObject("mesh0Cm");
            Iterator<String> mk = m0.keys();
            while (mk.hasNext()) {
                String k = mk.next();
                mesh0Cm.put(k, (float) m0.getDouble(k));
            }
            JSONObject caps = root.getJSONObject("residualCapCm");
            Iterator<String> ck = caps.keys();
            while (ck.hasNext()) {
                String k = ck.next();
                residualCapCm.put(k, (float) caps.getDouble(k));
            }
            JSONObject resMorph = root.getJSONObject("residualMorph");
            Iterator<String> rk = resMorph.keys();
            while (rk.hasNext()) {
                String k = rk.next();
                residualMorph.put(k, resMorph.getString(k));
            }
            if (root.has("lengthBones")) {
                JSONObject lb = root.getJSONObject("lengthBones");
                Iterator<String> lk = lb.keys();
                while (lk.hasNext()) {
                    String k = lk.next();
                    JSONArray arr = lb.getJSONArray(k);
                    String[] bones = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) {
                        bones[i] = arr.getString(i);
                    }
                    lengthBones.put(k, bones);
                }
            }

            JSONArray J = root.getJSONArray("jacobian");
            int rows = girthParts.size();
            int cols = betaNames.size();
            jacobian = new float[rows][cols];
            for (int r = 0; r < rows; r++) {
                JSONArray row = J.getJSONArray(r);
                for (int c = 0; c < cols; c++) {
                    jacobian[r][c] = (float) row.getDouble(c);
                }
            }
            loaded = !betaNames.isEmpty() && !girthParts.isEmpty();
            Log.i(TAG, "loaded betas=" + betaNames.size()
                    + " girths=" + girthParts.size()
                    + " residuals=" + residualCapCm.size());
            return loaded;
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            return false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<String> betaNames() {
        return betaNames;
    }

    public Map<String, String> residualMorphNames() {
        return residualMorph;
    }

    public Map<String, String[]> lengthBones() {
        return lengthBones;
    }

    public float getNativeHeightCm() {
        return nativeHeightCm;
    }

    /**
     * @param userCmByPart display cm (population-style) per BodyPart name
     * @param defaultCmByPart median/default display cm used as ratio base
     */
    public Result solve(Map<String, Float> userCmByPart,
                        Map<String, Float> defaultCmByPart) {
        Result out = new Result(betaNames.size());
        if (!loaded) {
            return out;
        }
        int nP = girthParts.size();
        int nB = betaNames.size();
        float[] meshTarget = new float[nP];
        float[] delta = new float[nP];
        for (int i = 0; i < nP; i++) {
            String part = girthParts.get(i);
            float m0 = mesh0Cm.containsKey(part) ? mesh0Cm.get(part) : 0f;
            float user = userCmByPart.containsKey(part) ? userCmByPart.get(part) : Float.NaN;
            float def = defaultCmByPart.containsKey(part) ? defaultCmByPart.get(part) : Float.NaN;
            if (Float.isNaN(user) || Float.isNaN(def) || def < 1e-3f || m0 < 1e-3f) {
                meshTarget[i] = m0;
            } else {
                meshTarget[i] = m0 * (user / def);
            }
            delta[i] = meshTarget[i] - m0;
        }

        float[] beta = solveRidge(jacobian, delta, ridge, nP, nB);
        for (int i = 0; i < nB; i++) {
            // Filament morph weight == beta unit; hard-cap to avoid mesh blow-up
            out.betas[i] = clamp(beta[i], -betaLimit, betaLimit);
        }

        // Residuals for all girth parts (including residual-only)
        for (String part : allGirthParts) {
            float m0 = mesh0Cm.containsKey(part) ? mesh0Cm.get(part) : 0f;
            float user = userCmByPart.containsKey(part) ? userCmByPart.get(part) : Float.NaN;
            float def = defaultCmByPart.containsKey(part) ? defaultCmByPart.get(part) : Float.NaN;
            float targetCm;
            if (Float.isNaN(user) || Float.isNaN(def) || def < 1e-3f || m0 < 1e-3f) {
                targetCm = m0;
            } else {
                targetCm = m0 * (user / def);
            }
            float pred = m0;
            int gi = girthParts.indexOf(part);
            if (gi >= 0) {
                for (int j = 0; j < nB; j++) {
                    pred += jacobian[gi][j] * out.betas[j];
                }
            }
            float remain = targetCm - pred;
            float cap = residualCapCm.containsKey(part) ? residualCapCm.get(part) : 0f;
            float w = 0f;
            if (cap > 1e-3f) {
                w = clamp(remain / cap, -residualMaxAbsW, residualMaxAbsW);
            }
            out.residualW.put(part, w);
        }

        // lengths: linear map median→1, p-range approx ±15% → scale
        for (String part : lengthBones.keySet()) {
            float user = userCmByPart.containsKey(part) ? userCmByPart.get(part) : Float.NaN;
            float def = defaultCmByPart.containsKey(part) ? defaultCmByPart.get(part) : Float.NaN;
            if (Float.isNaN(user) || Float.isNaN(def) || def < 1e-3f) {
                out.lengthScale.put(part, 1f);
                continue;
            }
            float ratio = user / def;
            // mild: 0.85..1.15 around median
            float s = clamp(ratio, lengthScaleMin, lengthScaleMax);
            out.lengthScale.put(part, s);
        }
        return out;
    }

    private static float[] solveRidge(float[][] J, float[] delta, float ridge,
                                      int nP, int nB) {
        // (J^T J + λI) β = J^T Δ
        float[][] A = new float[nB][nB];
        float[] b = new float[nB];
        for (int i = 0; i < nB; i++) {
            for (int j = 0; j < nB; j++) {
                float s = (i == j) ? ridge : 0f;
                for (int r = 0; r < nP; r++) {
                    s += J[r][i] * J[r][j];
                }
                A[i][j] = s;
            }
            float bb = 0f;
            for (int r = 0; r < nP; r++) {
                bb += J[r][i] * delta[r];
            }
            b[i] = bb;
        }
        return gaussJordan(A, b);
    }

    private static float[] gaussJordan(float[][] A, float[] b) {
        int n = b.length;
        float[][] M = new float[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }
        for (int i = 0; i < n; i++) {
            int piv = i;
            for (int r = i + 1; r < n; r++) {
                if (Math.abs(M[r][i]) > Math.abs(M[piv][i])) {
                    piv = r;
                }
            }
            float[] tmp = M[i];
            M[i] = M[piv];
            M[piv] = tmp;
            float div = M[i][i];
            if (Math.abs(div) < 1e-9f) {
                continue;
            }
            for (int c = i; c <= n; c++) {
                M[i][c] /= div;
            }
            for (int r = 0; r < n; r++) {
                if (r == i) {
                    continue;
                }
                float f = M[r][i];
                for (int c = i; c <= n; c++) {
                    M[r][c] -= f * M[i][c];
                }
            }
        }
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = M[i][n];
        }
        return x;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "SmplHybridSolver(loaded=%s, betas=%d, girths=%d)",
                loaded, betaNames.size(), girthParts.size());
    }
}
