package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 체형 분석 첫 화면에서 입력한 키·몸무게·나이를 기기에 보관한다.
 * 키는 예전부터 쓰던 "avatar_input"의 height_cm을 그대로 쓴다.
 * 나이는 회원가입의 생년월일이 연결되기 전까지 직접 입력으로 받는다.
 */
public final class BodyInput {

    private static final String PREF = "avatar_input";
    private static final String KEY_HEIGHT = "height_cm";
    private static final String KEY_WEIGHT = "weight_kg";
    private static final String KEY_AGE = "age_years";

    private BodyInput() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context context, float heightCm, float weightKg, float age) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (heightCm > 0f) {
            e.putFloat(KEY_HEIGHT, heightCm);
        }
        if (weightKg > 0f) {
            e.putFloat(KEY_WEIGHT, weightKg);
        }
        if (age > 0f) {
            e.putFloat(KEY_AGE, age);
        }
        e.apply();
    }

    /** 없으면 -1 */
    /** 탈퇴: 입력해 둔 키·몸무게·나이를 지운다 */
    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_HEIGHT)
                .remove(KEY_WEIGHT)
                .remove(KEY_AGE)
                .apply();
    }

    public static float height(Context context) {
        return prefs(context).getFloat(KEY_HEIGHT, -1f);
    }

    public static float weight(Context context) {
        return prefs(context).getFloat(KEY_WEIGHT, -1f);
    }

    public static float age(Context context) {
        return prefs(context).getFloat(KEY_AGE, -1f);
    }

    /** 저장된 키·몸무게·나이로 둘레를 어림한다. 하나라도 없거나 범위 밖이면 null. */
    public static BodyPrior.Result prior(Context context, float heightCm) {
        float h = heightCm > 0f ? heightCm : height(context);
        return BodyPrior.estimate(h, weight(context), age(context));
    }
}
