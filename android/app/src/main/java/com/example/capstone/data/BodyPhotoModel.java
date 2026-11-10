package com.example.capstone.data;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 정면·옆면 사진 + 키·몸무게·나이로 가슴·허리·엉덩이·허벅지 둘레를 어림하는 사진 모델 (ONNX, ResNet-18).
 * 모델은 "키·몸무게·나이 기준선 대비 얼마나 더하고 뺄지"를 맞힌다. training/README.md 9-4, 9-9 참고.
 *
 * 학습: AI Hub 151 여성 478명, 학습할 때 몸무게·키 입력에 평균 0, 표준편차 2.0의 오차를 섞고 사진은 저해상도·잡음 증강.
 * 출력은 4개(가슴·허리·엉덩이·허벅지). 옛 3개 출력 모델 파일이 들어와도 읽을 수 있게 meta의 길이를 따른다.
 * 19세 이상에서만 쓴다(학습 자료가 성인). 모델 파일: assets/models/body_photo.onnx, 보정 값: body_photo_meta.json
 */
public final class BodyPhotoModel implements Closeable {

    public static final String MODEL_ASSET = "models/body_photo.onnx";
    public static final String META_ASSET = "models/body_photo_meta.json";
    private static final String TAG = "BodyPhotoModel";

    private final OrtEnvironment env;
    private final OrtSession session;
    /** 모델이 내는 부위 순서. 앞에서부터 meta의 mu·sd 길이만큼 쓴다 */
    private static final int[] OUTPUT_PARTS = {BodyPrior.CHEST, BodyPrior.WAIST, BodyPrior.HIP, BodyPrior.THIGH};
    private final float[] mu;
    private final float[] sd;

    public BodyPhotoModel(Context context) throws Exception {
        JSONObject meta = new JSONObject(new String(readAsset(context, META_ASSET), "UTF-8"));
        JSONArray m = meta.getJSONArray("mu");
        JSONArray s = meta.getJSONArray("sd");
        int n = Math.min(m.length(), OUTPUT_PARTS.length);
        mu = new float[n];
        sd = new float[n];
        for (int i = 0; i < n; i++) {
            mu[i] = (float) m.getDouble(i);
            sd[i] = (float) s.getDouble(i);
        }
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(readAsset(context, MODEL_ASSET), new OrtSession.SessionOptions());
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        }
    }

    /**
     * @param front 정면 이미지 CHW (3×224×224, 정규화됨)
     * @param side  옆면 이미지 CHW
     * @return 가슴·허리·엉덩이·허벅지 둘레(cm). 모델이 못 돌면 null (옛 3개 출력 모델이면 길이 3)
     */
    public float[] circumferences(float[] front, float[] side, float heightCm, float weightKg, float age,
                                  float frontAspect, float sideAspect, BodyPrior.Result prior) {
        if (prior == null) {
            return null;
        }
        float[] tab = {
                (heightCm - 160f) / 8f, (weightKg - 55f) / 10f, (age - 35f) / 15f,
                (frontAspect - 0.4f) / 0.15f, (sideAspect - 0.4f) / 0.15f};
        long[] img = {1, 3, 224, 224};
        try (OnnxTensor f = OnnxTensor.createTensor(env, FloatBuffer.wrap(front), img);
             OnnxTensor sd1 = OnnxTensor.createTensor(env, FloatBuffer.wrap(side), img);
             OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(tab), new long[]{1, 5})) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("front", f);
            inputs.put("side", sd1);
            inputs.put("tab", t);
            try (OrtSession.Result result = session.run(inputs)) {
                float[][] out = (float[][]) result.get(0).getValue();
                if (out[0].length < mu.length) {
                    Log.w(TAG, "모델 출력 개수 " + out[0].length + "가 meta(" + mu.length + ")와 다릅니다");
                    return null;
                }
                float[] circ = new float[mu.length];
                for (int i = 0; i < circ.length; i++) {
                    circ[i] = prior.values[OUTPUT_PARTS[i]] + out[0][i] * sd[i] + mu[i];
                }
                return circ;
            }
        } catch (Exception e) {
            Log.w(TAG, "사진 모델을 돌리지 못했습니다", e);
            return null;
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }
}
